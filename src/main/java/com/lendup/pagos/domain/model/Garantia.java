package com.lendup.pagos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Agregado Garantía: controla constitución, retención y devolución. Invariante:
 * monto_afectado + monto_devuelto nunca supera monto_constituido.
 */
public final class Garantia {
  private final UUID id;
  private final UUID prestamoId;
  private final BigDecimal montoAcordado;
  private final String moneda;
  private EstadoGarantia estado;
  private BigDecimal montoConstituido;
  private BigDecimal montoAfectado;
  private BigDecimal montoDevuelto;
  private Instant constituidaEn;
  private Instant cerradaEn;
  private final Instant creadaEn;
  private Instant actualizadaEn;

  public Garantia(UUID id, UUID prestamoId, BigDecimal montoAcordado, String moneda, EstadoGarantia estado,
                  BigDecimal montoConstituido, BigDecimal montoAfectado, BigDecimal montoDevuelto,
                  Instant constituidaEn, Instant cerradaEn, Instant creadaEn, Instant actualizadaEn) {
    if (montoAcordado == null || montoAcordado.signum() <= 0) throw new IllegalArgumentException("La garantía requiere un monto positivo");
    if (moneda == null || !moneda.matches("[A-Z]{3}")) throw new IllegalArgumentException("Moneda inválida");
    this.id = id; this.prestamoId = prestamoId; this.montoAcordado = scale(montoAcordado); this.moneda = moneda; this.estado = estado;
    this.montoConstituido = montoConstituido == null ? null : scale(montoConstituido);
    this.montoAfectado = scale(montoAfectado == null ? BigDecimal.ZERO : montoAfectado);
    this.montoDevuelto = scale(montoDevuelto == null ? BigDecimal.ZERO : montoDevuelto);
    this.constituidaEn = constituidaEn; this.cerradaEn = cerradaEn; this.creadaEn = creadaEn; this.actualizadaEn = actualizadaEn;
  }

  public static Garantia nueva(UUID prestamoId, BigDecimal montoAcordado, String moneda, Instant now) {
    return new Garantia(UUID.randomUUID(), prestamoId, montoAcordado, moneda, EstadoGarantia.PENDIENTE, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, now, now);
  }

  public void constituir(BigDecimal monto, Instant now) {
    if (estado != EstadoGarantia.PENDIENTE) throw new IllegalStateException("La garantía ya no está pendiente");
    if (monto == null || scale(monto).compareTo(montoAcordado) != 0) throw new IllegalArgumentException("El monto constituido no coincide con el acordado");
    montoConstituido = montoAcordado; estado = EstadoGarantia.CONSTITUIDA; constituidaEn = now; actualizadaEn = now;
  }

  public void cancelar(Instant now) {
    if (estado != EstadoGarantia.PENDIENTE) throw new IllegalStateException("Solo una garantía pendiente puede cancelarse");
    estado = EstadoGarantia.CANCELADA; cerradaEn = now; actualizadaEn = now;
  }

  /** Aplica la parte afectada por una incidencia resuelta. */
  public void aplicarAfectacion(BigDecimal monto, Instant now) {
    exigirConstituida();
    BigDecimal value = scale(monto);
    if (value.signum() <= 0) throw new IllegalArgumentException("La afectación debe ser positiva");
    if (value.compareTo(saldoDisponible()) > 0) throw new IllegalArgumentException("La afectación supera el saldo de la garantía");
    montoAfectado = montoAfectado.add(value);
    estado = saldoDisponible().signum() == 0 ? EstadoGarantia.AFECTADA_TOTALMENTE : EstadoGarantia.AFECTADA_PARCIALMENTE;
    if (saldoDisponible().signum() == 0) cerradaEn = now;
    actualizadaEn = now;
  }

  /** Registra una devolución confirmada por el proveedor. */
  public void registrarDevolucion(BigDecimal monto, Instant now) {
    exigirConstituida();
    BigDecimal value = scale(monto);
    if (value.signum() <= 0) throw new IllegalArgumentException("La devolución debe ser positiva");
    if (value.compareTo(saldoDisponible()) > 0) throw new IllegalArgumentException("La devolución supera el saldo de la garantía");
    montoDevuelto = montoDevuelto.add(value);
    if (saldoDisponible().signum() == 0) {
      cerradaEn = now;
      if (montoAfectado.signum() == 0) estado = EstadoGarantia.DEVUELTA;
    }
    actualizadaEn = now;
  }

  public BigDecimal saldoDisponible() {
    if (montoConstituido == null) return BigDecimal.ZERO.setScale(2);
    return montoConstituido.subtract(montoAfectado).subtract(montoDevuelto);
  }

  public boolean constituida() { return montoConstituido != null; }

  private void exigirConstituida() {
    if (montoConstituido == null || estado == EstadoGarantia.PENDIENTE || estado == EstadoGarantia.CANCELADA) {
      throw new IllegalStateException("La garantía no está constituida");
    }
  }

  private static BigDecimal scale(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

  public UUID id() { return id; } public UUID prestamoId() { return prestamoId; } public BigDecimal montoAcordado() { return montoAcordado; }
  public String moneda() { return moneda; } public EstadoGarantia estado() { return estado; } public BigDecimal montoConstituido() { return montoConstituido; }
  public BigDecimal montoAfectado() { return montoAfectado; } public BigDecimal montoDevuelto() { return montoDevuelto; }
  public Instant constituidaEn() { return constituidaEn; } public Instant cerradaEn() { return cerradaEn; }
  public Instant creadaEn() { return creadaEn; } public Instant actualizadaEn() { return actualizadaEn; }
}
