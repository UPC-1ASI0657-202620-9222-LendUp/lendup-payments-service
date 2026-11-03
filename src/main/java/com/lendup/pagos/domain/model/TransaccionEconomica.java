package com.lendup.pagos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Operación económica asociada a un préstamo; controla las transiciones de estado permitidas. */
public final class TransaccionEconomica {
  private final UUID id;
  private final UUID prestamoId;
  private final UUID garantiaId;
  private final UUID cambioFechaPrestamoId;
  private final UUID incidenciaId;
  private final TipoTransaccion tipo;
  private EstadoTransaccion estado;
  private final BigDecimal monto;
  private final String moneda;
  private BigDecimal comisionProveedor;
  private final String medioPagoSeleccionado;
  private String referenciaProveedor;
  private final String claveIdempotencia;
  private String checkoutUrl;
  private final Instant solicitadaEn;
  private Instant confirmadaEn;
  private Instant actualizadaEn;

  public TransaccionEconomica(UUID id, UUID prestamoId, UUID garantiaId, UUID cambioFechaPrestamoId, UUID incidenciaId,
                              TipoTransaccion tipo, EstadoTransaccion estado, BigDecimal monto, String moneda,
                              BigDecimal comisionProveedor, String medioPagoSeleccionado, String referenciaProveedor,
                              String claveIdempotencia, String checkoutUrl, Instant solicitadaEn, Instant confirmadaEn,
                              Instant actualizadaEn) {
    if (prestamoId == null || tipo == null || estado == null) throw new IllegalArgumentException("Transacción incompleta");
    if (monto == null || monto.signum() <= 0) throw new IllegalArgumentException("El monto debe ser positivo");
    if (claveIdempotencia == null || claveIdempotencia.isBlank()) throw new IllegalArgumentException("Se requiere clave_idempotencia");
    this.id = id; this.prestamoId = prestamoId; this.garantiaId = garantiaId; this.cambioFechaPrestamoId = cambioFechaPrestamoId;
    this.incidenciaId = incidenciaId; this.tipo = tipo; this.estado = estado; this.monto = monto.setScale(2, RoundingMode.HALF_UP);
    this.moneda = moneda; this.comisionProveedor = comisionProveedor; this.medioPagoSeleccionado = medioPagoSeleccionado;
    this.referenciaProveedor = referenciaProveedor; this.claveIdempotencia = claveIdempotencia; this.checkoutUrl = checkoutUrl;
    this.solicitadaEn = solicitadaEn; this.confirmadaEn = confirmadaEn; this.actualizadaEn = actualizadaEn;
  }

  /** Crea una operación en PENDIENTE; las internas (liberación o retención) se registran directamente confirmadas. */
  public static TransaccionEconomica nueva(UUID prestamoId, UUID garantiaId, UUID cambioFechaPrestamoId, UUID incidenciaId,
                                          TipoTransaccion tipo, BigDecimal monto, String moneda, String medio,
                                          String referenciaProveedor, String claveIdempotencia, Instant now) {
    return new TransaccionEconomica(UUID.randomUUID(), prestamoId, garantiaId, cambioFechaPrestamoId, incidenciaId, tipo,
        EstadoTransaccion.PENDIENTE, monto, moneda, null, medio, referenciaProveedor, claveIdempotencia, null, now, null, now);
  }

  public void asignarCheckout(String url, Instant now) {
    if (estado != EstadoTransaccion.PENDIENTE) throw new IllegalStateException("La transacción ya no está pendiente");
    checkoutUrl = url; actualizadaEn = now;
  }

  /**
   * Aplica el resultado de un cobro informado por el proveedor. Una transacción CONFIRMADA no retrocede; una
   * RECHAZADA o CANCELADA puede pasar a CONFIRMADA si el prestatario reintenta y aprueba dentro del mismo checkout.
   * Devuelve true si el estado cambió.
   */
  public boolean aplicarResultadoCobro(ResultadoProveedor resultado, String referencia, BigDecimal comision, Instant now) {
    if (!tipo.esCobroAlPrestatario()) throw new IllegalStateException("La transacción no es un cobro al prestatario");
    if (estado == EstadoTransaccion.CONFIRMADA || estado == EstadoTransaccion.FALLIDA) return false;
    EstadoTransaccion siguiente = switch (resultado) {
      case APROBADO -> EstadoTransaccion.CONFIRMADA;
      case RECHAZADO -> estado == EstadoTransaccion.PENDIENTE ? EstadoTransaccion.RECHAZADA : estado;
      case CANCELADO -> estado == EstadoTransaccion.PENDIENTE ? EstadoTransaccion.CANCELADA : estado;
      case EN_PROCESO -> estado;
    };
    if (referencia != null && !referencia.isBlank()) referenciaProveedor = referencia;
    if (siguiente == estado) { actualizadaEn = now; return false; }
    estado = siguiente;
    if (siguiente == EstadoTransaccion.CONFIRMADA) { confirmadaEn = now; comisionProveedor = comision; }
    actualizadaEn = now;
    return true;
  }

  /** Confirma operaciones internas o reembolsos aceptados por el proveedor. */
  public void confirmar(Instant now) {
    if (estado != EstadoTransaccion.PENDIENTE) throw new IllegalStateException("La transacción ya no está pendiente");
    estado = EstadoTransaccion.CONFIRMADA; confirmadaEn = now; actualizadaEn = now;
  }

  /** Rechazo definitivo de un reembolso por parte del proveedor. */
  public void fallar(Instant now) {
    if (estado != EstadoTransaccion.PENDIENTE) throw new IllegalStateException("La transacción ya no está pendiente");
    estado = EstadoTransaccion.FALLIDA; actualizadaEn = now;
  }

  /** Un cobro pendiente que ya no corresponde (reserva cancelada antes de pagar). */
  public void cancelar(Instant now) {
    if (estado != EstadoTransaccion.PENDIENTE) throw new IllegalStateException("La transacción ya no está pendiente");
    estado = EstadoTransaccion.CANCELADA; actualizadaEn = now;
  }

  public UUID id() { return id; } public UUID prestamoId() { return prestamoId; } public UUID garantiaId() { return garantiaId; }
  public UUID cambioFechaPrestamoId() { return cambioFechaPrestamoId; } public UUID incidenciaId() { return incidenciaId; }
  public TipoTransaccion tipo() { return tipo; } public EstadoTransaccion estado() { return estado; } public BigDecimal monto() { return monto; }
  public String moneda() { return moneda; } public BigDecimal comisionProveedor() { return comisionProveedor; }
  public String medioPagoSeleccionado() { return medioPagoSeleccionado; } public String referenciaProveedor() { return referenciaProveedor; }
  public String claveIdempotencia() { return claveIdempotencia; } public String checkoutUrl() { return checkoutUrl; }
  public Instant solicitadaEn() { return solicitadaEn; } public Instant confirmadaEn() { return confirmadaEn; } public Instant actualizadaEn() { return actualizadaEn; }
}
