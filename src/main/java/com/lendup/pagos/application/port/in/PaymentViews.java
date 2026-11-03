package com.lendup.pagos.application.port.in;

import com.lendup.pagos.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Comandos y vistas de los casos de uso (independientes del formato JSON de la API). */
public final class PaymentViews {
  private PaymentViews() {}

  public record CrearPagoCommand(UUID prestamoId, UUID cambioFechaPrestamoId, String tipo, BigDecimal monto,
                                 String medioPagoSeleccionado, String claveIdempotencia) {}

  public record ConstituirGarantiaCommand(UUID prestamoId, String medioPagoSeleccionado, String claveIdempotencia) {}

  public record ReservaConfirmadaCommand(UUID reservaId, UUID prestamistaUsuarioId, UUID prestatarioUsuarioId, LocalDateTime desde,
                                         LocalDateTime hasta, BigDecimal tarifaDiariaAcordada, BigDecimal garantiaMonetariaAcordada,
                                         String moneda) {}

  public record IncidenciaResueltaCommand(UUID incidenciaId, UUID prestamoId, DecisionGarantia decision, BigDecimal montoAfectado) {}

  public record MedioPagoView(String codigo, String nombre, String estado) {}

  public record GarantiaView(UUID id, UUID prestamoId, BigDecimal montoAcordado, String moneda, String estado, BigDecimal montoConstituido,
                             BigDecimal montoAfectado, BigDecimal montoDevuelto, Instant constituidaEn, Instant cerradaEn,
                             Instant creadaEn, Instant actualizadaEn) {
    public static GarantiaView from(Garantia g) {
      return new GarantiaView(g.id(), g.prestamoId(), g.montoAcordado(), g.moneda(), g.estado().name(), g.montoConstituido(),
          g.montoAfectado(), g.montoDevuelto(), g.constituidaEn(), g.cerradaEn(), g.creadaEn(), g.actualizadaEn());
    }
  }

  public record TransaccionView(UUID id, UUID prestamoId, UUID garantiaId, UUID cambioFechaPrestamoId, UUID incidenciaId, String tipo,
                                String estado, BigDecimal monto, String moneda, BigDecimal comisionProveedor, String medioPagoSeleccionado,
                                String referenciaProveedor, String claveIdempotencia, String checkoutUrl, Instant solicitadaEn,
                                Instant confirmadaEn, Instant actualizadaEn, GarantiaView garantia) {
    public static TransaccionView from(TransaccionEconomica t) { return from(t, null); }
    public static TransaccionView from(TransaccionEconomica t, Garantia g) {
      return new TransaccionView(t.id(), t.prestamoId(), t.garantiaId(), t.cambioFechaPrestamoId(), t.incidenciaId(), t.tipo().name(),
          t.estado().name(), t.monto(), t.moneda(), t.comisionProveedor(), t.medioPagoSeleccionado(), t.referenciaProveedor(),
          t.claveIdempotencia(), t.checkoutUrl(), t.solicitadaEn(), t.confirmadaEn(), t.actualizadaEn(), g == null ? null : GarantiaView.from(g));
    }
  }
}
