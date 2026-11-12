package com.lendup.pagos.infrastructure.messaging;

import com.lendup.pagos.config.RabbitConfiguration;
import com.lendup.pagos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.pagos.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox Event Publisher (Fig. 18). Publica con publisher confirms y mandatory=true (igual que Reservas): el evento
 * solo se marca publicado si el broker lo confirma Y lo entregó al menos a una cola. Si nadie está suscrito a la
 * routing key, el broker lo devuelve y el evento sigue pendiente (no se pierde) con reintentos espaciados.
 */
@Component
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private final OutboxJpaRepository repo;
  private final RabbitTemplate rabbit;
  private final JsonMapper json;
  private final Clock clock;
  private final int maxAttempts;

  public OutboxRelay(OutboxJpaRepository repo, RabbitTemplate rabbit, JsonMapper json, Clock clock,
                     @Value("${lendup.outbox.max-attempts:10}") int maxAttempts) {
    this.repo = repo; this.rabbit = rabbit; this.json = json; this.clock = clock; this.maxAttempts = maxAttempts;
  }

  @Scheduled(fixedDelayString = "${lendup.outbox.relay-delay-ms:1000}")
  @Transactional
  public void relay() {
    for (OutboxEventEntity e : repo.pending(clock.instant(), maxAttempts, PageRequest.of(0, 50))) {
      try {
        publish(e);
        e.published(clock.instant());
      } catch (Exception ex) {
        Instant next = clock.instant().plusSeconds(Math.min(300, 1L << Math.min(e.getAttempts() + 1, 8)));
        e.failed(ex.getMessage(), next);
        if (e.getAttempts() >= maxAttempts) log.error("outbox_event_exhausted event_id={} event_type={} attempts={} error={}", e.getId(), e.getEventType(), e.getAttempts(), e.getLastError());
        else log.warn("outbox_publish_failed event_id={} event_type={} attempts={} error={}", e.getId(), e.getEventType(), e.getAttempts(), e.getLastError());
      }
      repo.save(e);
    }
  }

  private void publish(OutboxEventEntity e) throws Exception {
    String routingKey = routingKey(e.getEventType());
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("event_id", e.getId());
    envelope.put("event_type", e.getEventType());
    envelope.put("event_version", e.getEventVersion());
    envelope.put("aggregate_version", e.getAggregateVersion());
    envelope.put("aggregate_id", e.getAggregateId());
    envelope.put("occurred_at", e.getOccurredAt());
    envelope.put("correlation_id", e.getCorrelationId());
    envelope.put("payload", json.readValue(e.getPayload(), Map.class));
    CorrelationData cd = new CorrelationData(e.getId().toString());
    rabbit.convertAndSend(RabbitConfiguration.EVENTS_EXCHANGE, routingKey, json.writeValueAsBytes(envelope), message -> {
      message.getMessageProperties().setContentType("application/json");
      message.getMessageProperties().setMessageId(e.getId().toString());
      message.getMessageProperties().setHeader("x-event-type", e.getEventType());
      return message;
    }, cd);
    CorrelationData.Confirm confirm = cd.getFuture().get(5, TimeUnit.SECONDS);
    if (!confirm.ack()) throw new IllegalStateException("NACK del broker: " + confirm.reason());
    if (cd.getReturned() != null) throw new IllegalStateException("Ninguna cola suscrita a " + routingKey + " (el broker devolvió el evento)");
  }

  static String routingKey(String type) {
    return switch (type) {
      case "PagoConfirmado" -> "pagos.pago-confirmado.v1";
      case "PagoRechazado" -> "pagos.pago-rechazado.v1";
      case "GarantiaConstituida" -> "pagos.garantia-constituida.v1";
      case "TarifaLiberada" -> "pagos.tarifa-liberada.v1";
      case "GarantiaDevuelta" -> "pagos.garantia-devuelta.v1";
      case "GarantiaRetenida" -> "pagos.garantia-retenida.v1";
      case "ReembolsoProcesado" -> "pagos.reembolso-procesado.v1";
      default -> throw new IllegalArgumentException("Tipo de evento Outbox no soportado: " + type);
    };
  }
}
