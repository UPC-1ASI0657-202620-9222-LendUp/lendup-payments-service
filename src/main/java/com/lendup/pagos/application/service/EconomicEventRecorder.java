package com.lendup.pagos.application.service;

import com.lendup.pagos.application.port.out.OutboxPort;
import com.lendup.pagos.domain.event.EventEnvelope;
import com.lendup.pagos.domain.model.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Construye los eventos económicos publicados por Pagos y Garantías y los agrega al outbox dentro de la
 * transacción del caso de uso. aggregate_version es la marca de tiempo en milisegundos del cambio, creciente por
 * agregado, para que los consumidores puedan descartar eventos atrasados.
 */
@Component
public class EconomicEventRecorder {
  private final OutboxPort outbox;
  private final Clock clock;

  public EconomicEventRecorder(OutboxPort outbox, Clock clock) { this.outbox = outbox; this.clock = clock; }

  public void pagoConfirmado(TransaccionEconomica t, PrestamoPagable p, String correlationId) {
    Map<String, Object> payload = transaccion(t, p);
    payload.put("confirmada_en", t.confirmadaEn());
    append("PagoConfirmado", t.id(), payload, correlationId);
  }

  public void pagoRechazado(TransaccionEconomica t, PrestamoPagable p, String detalle, String correlationId) {
    Map<String, Object> payload = transaccion(t, p);
    payload.put("motivo", detalle == null ? "" : detalle);
    append("PagoRechazado", t.id(), payload, correlationId);
  }

  public void garantiaConstituida(Garantia g, TransaccionEconomica t, PrestamoPagable p, String correlationId) {
    Map<String, Object> payload = garantia(g, t, p);
    payload.put("monto_constituido", g.montoConstituido());
    payload.put("constituida_en", g.constituidaEn());
    append("GarantiaConstituida", g.id(), payload, correlationId);
  }

  public void tarifaLiberada(TransaccionEconomica liberacion, TransaccionEconomica pago, PrestamoPagable p, String correlationId) {
    Map<String, Object> payload = transaccion(liberacion, p);
    BigDecimal comision = pago.comisionProveedor() == null ? BigDecimal.ZERO.setScale(2) : pago.comisionProveedor();
    payload.put("transaccion_pago_id", pago.id());
    payload.put("comision_proveedor", comision);
    payload.put("monto_neto_prestamista", pago.monto().subtract(comision));
    append("TarifaLiberada", liberacion.id(), payload, correlationId);
  }

  public void garantiaDevuelta(Garantia g, TransaccionEconomica devolucion, PrestamoPagable p, String referenciaReembolso, String correlationId) {
    Map<String, Object> payload = garantia(g, devolucion, p);
    payload.put("monto_devuelto", devolucion.monto());
    payload.put("monto_devuelto_total", g.montoDevuelto());
    payload.put("referencia_reembolso", referenciaReembolso == null ? "" : referenciaReembolso);
    append("GarantiaDevuelta", g.id(), payload, correlationId);
  }

  public void garantiaRetenida(Garantia g, TransaccionEconomica retencion, PrestamoPagable p, DecisionGarantia decision, String correlationId) {
    Map<String, Object> payload = garantia(g, retencion, p);
    payload.put("decision_garantia", decision.name());
    payload.put("monto_retenido", retencion.monto());
    payload.put("monto_afectado_total", g.montoAfectado());
    payload.put("saldo_garantia", g.saldoDisponible());
    append("GarantiaRetenida", g.id(), payload, correlationId);
  }

  public void reembolsoProcesado(TransaccionEconomica reembolso, PrestamoPagable p, String referenciaReembolso, String correlationId) {
    Map<String, Object> payload = transaccion(reembolso, p);
    payload.put("referencia_reembolso", referenciaReembolso == null ? "" : referenciaReembolso);
    append("ReembolsoProcesado", reembolso.id(), payload, correlationId);
  }

  private Map<String, Object> transaccion(TransaccionEconomica t, PrestamoPagable p) {
    Map<String, Object> payload = participantes(t.prestamoId(), p);
    payload.put("transaccion_id", t.id());
    payload.put("tipo", t.tipo().name());
    payload.put("estado", t.estado().name());
    payload.put("monto", t.monto());
    payload.put("moneda", t.moneda());
    if (t.garantiaId() != null) payload.put("garantia_id", t.garantiaId());
    if (t.cambioFechaPrestamoId() != null) payload.put("cambio_fecha_prestamo_id", t.cambioFechaPrestamoId());
    if (t.incidenciaId() != null) payload.put("incidencia_id", t.incidenciaId());
    if (t.comisionProveedor() != null) payload.put("comision_proveedor", t.comisionProveedor());
    if (t.referenciaProveedor() != null) payload.put("referencia_proveedor", t.referenciaProveedor());
    return payload;
  }

  private Map<String, Object> garantia(Garantia g, TransaccionEconomica t, PrestamoPagable p) {
    Map<String, Object> payload = participantes(g.prestamoId(), p);
    payload.put("garantia_id", g.id());
    payload.put("transaccion_id", t.id());
    payload.put("estado_garantia", g.estado().name());
    payload.put("moneda", g.moneda());
    if (t.incidenciaId() != null) payload.put("incidencia_id", t.incidenciaId());
    return payload;
  }

  private Map<String, Object> participantes(UUID prestamoId, PrestamoPagable p) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("prestamo_id", prestamoId);
    if (p != null) {
      payload.put("reserva_id", p.reservaId());
      payload.put("prestatario_usuario_id", p.prestatarioUsuarioId());
      payload.put("prestamista_usuario_id", p.prestamistaUsuarioId());
    }
    return payload;
  }

  private void append(String type, UUID aggregateId, Map<String, Object> payload, String correlationId) {
    Instant now = clock.instant();
    outbox.append(new EventEnvelope(UUID.randomUUID(), type, 1, now.toEpochMilli(), aggregateId, now,
        correlationId == null ? "" : correlationId, payload));
  }
}
