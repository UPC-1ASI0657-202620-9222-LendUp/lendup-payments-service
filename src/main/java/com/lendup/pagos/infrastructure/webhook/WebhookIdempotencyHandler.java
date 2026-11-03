package com.lendup.pagos.infrastructure.webhook;

import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.port.in.PaymentUseCases;
import com.lendup.pagos.application.port.out.PaymentGatewayPort;
import com.lendup.pagos.application.port.out.PaymentGatewayPort.ProviderPayment;
import com.lendup.pagos.infrastructure.persistence.entity.NotificacionProveedorEntity;
import com.lendup.pagos.infrastructure.persistence.repository.NotificacionProveedorJpaRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * Webhook Idempotency Handler (Fig. 18): valida la firma, registra la notificación en notificaciones_proveedor y
 * descarta las ya procesadas. Luego consulta el pago en Mercado Pago (fuente de verdad, nunca el cuerpo del
 * webhook) y delega en el Payment Application Service, que actualiza estado, auditoría y outbox en una transacción.
 */
@Component
public class WebhookIdempotencyHandler {
  public record WebhookResult(String proveedorEventoId, String estado) {}

  private final MercadoPagoSignatureValidator signature;
  private final NotificacionProveedorJpaRepository notifications;
  private final PaymentGatewayPort gateway;
  private final PaymentUseCases payments;
  private final JsonMapper json;
  private final TransactionOperations tx;
  private final Clock clock;

  public WebhookIdempotencyHandler(MercadoPagoSignatureValidator signature, NotificacionProveedorJpaRepository notifications, PaymentGatewayPort gateway,
                                   PaymentUseCases payments, JsonMapper json, TransactionOperations tx, Clock clock) {
    this.signature = signature; this.notifications = notifications; this.gateway = gateway; this.payments = payments; this.json = json; this.tx = tx; this.clock = clock;
  }

  public WebhookResult handle(String signatureHeader, String requestId, String queryDataId, String queryType, Map<String, Object> body, String correlationId) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    String dataId = blank(queryDataId) ? dataId(payload) : queryDataId;
    String type = Objects.toString(payload.getOrDefault("type", payload.getOrDefault("topic", queryType == null ? "" : queryType)), "");
    signature.validate(signatureHeader, requestId, dataId);
    String eventoId = payload.get("id") != null ? "mp:" + payload.get("id") : "mp:" + type + ":" + dataId + ":" + Objects.toString(requestId, "");
    NotificacionProveedorEntity notification = registrar(eventoId, type, payload);
    if ("PROCESADA".equals(notification.getEstado()) || "IGNORADA".equals(notification.getEstado())) return new WebhookResult(eventoId, "DUPLICADA");
    if (!"payment".equals(type) || blank(dataId)) return marcar(notification.getId(), eventoId, "IGNORADA");
    try {
      Optional<ProviderPayment> payment = gateway.consultarPago(dataId);
      if (payment.isEmpty()) return marcar(notification.getId(), eventoId, "IGNORADA");
      List<UUID> reembolsos = payments.aplicarResultadoCobro(payment.get(), correlationId);
      WebhookResult result = marcar(notification.getId(), eventoId, "PROCESADA");
      reembolsos.forEach(id -> payments.ejecutarReembolso(id, correlationId));
      return result;
    } catch (DependencyUnavailableException e) {
      // Se responde 503 para que Mercado Pago reintente; la notificación en ERROR se reprocesa al reintento.
      marcar(notification.getId(), eventoId, "ERROR");
      throw e;
    }
  }

  private NotificacionProveedorEntity registrar(String eventoId, String type, Map<String, Object> payload) {
    String raw = json.writeValueAsString(payload);
    try {
      return tx.execute(status -> notifications.findByProveedorEventoId(eventoId)
          .orElseGet(() -> notifications.saveAndFlush(new NotificacionProveedorEntity(UUID.randomUUID(), eventoId, type.isBlank() ? "desconocido" : type, raw, clock.instant()))));
    } catch (DataIntegrityViolationException concurrent) {
      return tx.execute(status -> notifications.findByProveedorEventoId(eventoId).orElseThrow());
    }
  }

  private WebhookResult marcar(UUID id, String eventoId, String estado) {
    tx.executeWithoutResult(status -> notifications.findById(id).ifPresent(n -> { n.marcar(estado, clock.instant()); notifications.save(n); }));
    return new WebhookResult(eventoId, estado);
  }

  private static String dataId(Map<String, Object> payload) {
    return payload.get("data") instanceof Map<?, ?> data && data.get("id") != null ? data.get("id").toString() : null;
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }
}
