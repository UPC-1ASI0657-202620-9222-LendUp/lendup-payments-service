package com.lendup.pagos.infrastructure.messaging;

import com.lendup.pagos.application.port.in.GuaranteeUseCases;
import com.lendup.pagos.application.port.in.PaymentUseCases;
import com.lendup.pagos.application.port.in.PaymentViews.IncidenciaResueltaCommand;
import com.lendup.pagos.application.port.in.PaymentViews.ReservaConfirmadaCommand;
import com.lendup.pagos.config.EventRoutingProperties;
import com.lendup.pagos.config.RabbitConfiguration;
import com.lendup.pagos.domain.model.DecisionGarantia;
import com.lendup.pagos.infrastructure.persistence.entity.ProcessedEventEntity;
import com.lendup.pagos.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Economic Event Consumer (Fig. 18). Inbox: el event_id se registra en processed_events en la misma transacción
 * que el cambio de negocio; un duplicado se descarta. Los errores se reintentan y luego van a la DLQ. Los
 * reembolsos creados se envían a Mercado Pago después del commit.
 */
@Component
public class EconomicEventConsumer {
  private static final Logger log = LoggerFactory.getLogger(EconomicEventConsumer.class);
  private final JsonMapper json;
  private final ProcessedEventJpaRepository processed;
  private final PaymentUseCases payments;
  private final GuaranteeUseCases guarantees;
  private final EventRoutingProperties routes;
  private final Clock clock;

  public EconomicEventConsumer(JsonMapper json, ProcessedEventJpaRepository processed, PaymentUseCases payments, GuaranteeUseCases guarantees,
                               EventRoutingProperties routes, Clock clock) {
    this.json = json; this.processed = processed; this.payments = payments; this.guarantees = guarantees; this.routes = routes; this.clock = clock;
  }

  @RabbitListener(queues = RabbitConfiguration.QUEUE)
  @Transactional
  public void consume(Message message) {
    handle(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
  }

  @Transactional
  public void handle(String routingKey, byte[] body) {
    Map<String, Object> event = json.readValue(body, new TypeReference<Map<String, Object>>() {});
    UUID eventId = UUID.fromString(text(event, "event_id"));
    if (processed.existsById(eventId)) return;
    String correlationId = Objects.toString(event.getOrDefault("correlation_id", ""), "");
    @SuppressWarnings("unchecked") Map<String, Object> payload = (Map<String, Object>) event.get("payload");
    if (payload == null) throw new IllegalArgumentException("Evento sin payload");
    List<UUID> reembolsos = dispatch(routingKey, payload, correlationId);
    long version = event.get("aggregate_version") instanceof Number n ? n.longValue() : 0L;
    UUID aggregateId = event.get("aggregate_id") == null ? new UUID(0, 0) : UUID.fromString(event.get("aggregate_id").toString());
    processed.save(new ProcessedEventEntity(eventId, RabbitConfiguration.QUEUE, aggregateId, version, clock.instant()));
    if (!reembolsos.isEmpty()) ejecutarDespuesDelCommit(reembolsos, correlationId);
  }

  private List<UUID> dispatch(String routingKey, Map<String, Object> p, String correlationId) {
    if (RabbitConfiguration.RESERVATION_CONFIRMED.equals(routingKey)) {
      payments.registrarReservaConfirmada(new ReservaConfirmadaCommand(uuid(p, "reserva_id"), uuid(p, "prestamista_usuario_id"),
          uuid(p, "prestatario_usuario_id"), LocalDateTime.parse(text(p, "desde")), LocalDateTime.parse(text(p, "hasta")),
          decimal(p, "tarifa_diaria_acordada"), optionalDecimal(p, "garantia_monetaria_acordada"), text(p, "moneda_acordada")), correlationId);
      return List.of();
    }
    if (RabbitConfiguration.RESERVATION_CANCELLED.equals(routingKey)) return payments.registrarReservaCancelada(uuid(p, "reserva_id"), correlationId);
    if (routes.loanCreatedRoutingKey().equals(routingKey)) { payments.vincularPrestamo(uuid(p, "reserva_id"), uuid(p, "prestamo_id"), correlationId); return List.of(); }
    if (routes.loanActivatedRoutingKey().equals(routingKey)) { payments.registrarPrestamoActivado(uuid(p, "prestamo_id"), correlationId); return List.of(); }
    if (routes.extensionAcceptedRoutingKey().equals(routingKey)) {
      payments.registrarExtensionAceptada(uuid(p, "prestamo_id"), uuid(p, "cambio_fecha_prestamo_id"), decimal(p, "costo_adicional"), correlationId);
      return List.of();
    }
    if (routes.loanFinishedRoutingKey().equals(routingKey)) return guarantees.registrarPrestamoFinalizado(uuid(p, "prestamo_id"), correlationId);
    if (routes.incidentResolvedRoutingKey().equals(routingKey)) {
      return guarantees.registrarIncidenciaResuelta(new IncidenciaResueltaCommand(uuid(p, "incidencia_id"), uuid(p, "prestamo_id"),
          DecisionGarantia.valueOf(text(p, "decision_garantia")), optionalDecimal(p, "monto_garantia_afectado")), correlationId);
    }
    throw new IllegalArgumentException("Routing key no soportada: " + routingKey);
  }

  private void ejecutarDespuesDelCommit(List<UUID> reembolsos, String correlationId) {
    Runnable ejecutar = () -> reembolsos.forEach(id -> {
      try { payments.ejecutarReembolso(id, correlationId); }
      catch (RuntimeException e) { log.error("No se pudo ejecutar el reembolso {}; la conciliación lo reintentará", id, e); }
    });
    if (!TransactionSynchronizationManager.isSynchronizationActive()) { ejecutar.run(); return; }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { ejecutar.run(); }
    });
  }

  private static String text(Map<String, Object> m, String k) {
    Object v = m.get(k);
    if (v == null || v.toString().isBlank()) throw new IllegalArgumentException("Evento sin " + k);
    return v.toString();
  }
  private static UUID uuid(Map<String, Object> m, String k) { return UUID.fromString(text(m, k)); }
  private static BigDecimal decimal(Map<String, Object> m, String k) { return new BigDecimal(text(m, k)); }
  private static BigDecimal optionalDecimal(Map<String, Object> m, String k) { return m.get(k) == null ? null : new BigDecimal(m.get(k).toString()); }
}
