package com.lendup.pagos.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proyección local de la operación (reserva confirmada + préstamo) con las condiciones económicas acordadas.
 * Permite validar participación y montos sin leer la base de datos de otros servicios.
 */
public final class PrestamoPagable {
  private final UUID reservaId;
  private UUID prestamoId;
  private final UUID prestamistaUsuarioId;
  private final UUID prestatarioUsuarioId;
  private final PeriodoPrestamo periodo;
  private final BigDecimal tarifaDiariaAcordada;
  private final BigDecimal garantiaMonetariaAcordada;
  private final String moneda;
  private EstadoPrestamoPagable estado;
  private BigDecimal costoExtensionPendiente;
  private UUID cambioFechaPendienteId;
  private final Instant creadoEn;
  private Instant actualizadoEn;

  public PrestamoPagable(UUID reservaId, UUID prestamoId, UUID prestamistaUsuarioId, UUID prestatarioUsuarioId, PeriodoPrestamo periodo,
                         BigDecimal tarifaDiariaAcordada, BigDecimal garantiaMonetariaAcordada, String moneda, EstadoPrestamoPagable estado,
                         BigDecimal costoExtensionPendiente, UUID cambioFechaPendienteId, Instant creadoEn, Instant actualizadoEn) {
    if (reservaId == null || prestamistaUsuarioId == null || prestatarioUsuarioId == null || periodo == null) throw new IllegalArgumentException("Proyección incompleta");
    if (tarifaDiariaAcordada == null || tarifaDiariaAcordada.signum() <= 0) throw new IllegalArgumentException("Tarifa diaria inválida");
    if (moneda == null || !moneda.matches("[A-Z]{3}")) throw new IllegalArgumentException("Moneda inválida");
    this.reservaId = reservaId; this.prestamoId = prestamoId; this.prestamistaUsuarioId = prestamistaUsuarioId; this.prestatarioUsuarioId = prestatarioUsuarioId;
    this.periodo = periodo; this.tarifaDiariaAcordada = tarifaDiariaAcordada; this.garantiaMonetariaAcordada = garantiaMonetariaAcordada; this.moneda = moneda;
    this.estado = estado; this.costoExtensionPendiente = costoExtensionPendiente; this.cambioFechaPendienteId = cambioFechaPendienteId;
    this.creadoEn = creadoEn; this.actualizadoEn = actualizadoEn;
  }

  public static PrestamoPagable desdeReservaConfirmada(UUID reservaId, UUID prestamistaId, UUID prestatarioId, PeriodoPrestamo periodo,
                                                       BigDecimal tarifaDiaria, BigDecimal garantia, String moneda, Instant now) {
    return new PrestamoPagable(reservaId, null, prestamistaId, prestatarioId, periodo, tarifaDiaria, garantia, moneda,
        EstadoPrestamoPagable.RESERVADO, null, null, now, now);
  }

  public void vincularPrestamo(UUID id, Instant now) {
    if (prestamoId != null && !prestamoId.equals(id)) throw new IllegalStateException("La reserva ya está vinculada a otro préstamo");
    prestamoId = id; actualizadoEn = now;
  }

  public void activar(Instant now) { if (estado == EstadoPrestamoPagable.RESERVADO) { estado = EstadoPrestamoPagable.ACTIVO; actualizadoEn = now; } }
  public void finalizar(Instant now) { if (estado != EstadoPrestamoPagable.CANCELADO) { estado = EstadoPrestamoPagable.FINALIZADO; actualizadoEn = now; } }
  public void cancelar(Instant now) { if (estado == EstadoPrestamoPagable.RESERVADO) { estado = EstadoPrestamoPagable.CANCELADO; actualizadoEn = now; } }

  public void registrarExtensionAceptada(UUID cambioFechaId, BigDecimal costo, Instant now) {
    if (costo == null || costo.signum() <= 0) throw new IllegalArgumentException("El costo de la extensión debe ser positivo");
    cambioFechaPendienteId = cambioFechaId; costoExtensionPendiente = costo; actualizadoEn = now;
  }

  public void liquidarExtension(Instant now) { cambioFechaPendienteId = null; costoExtensionPendiente = null; actualizadoEn = now; }

  public BigDecimal tarifaTotal(PoliticaDeCosto politica) { return politica.importe(periodo, tarifaDiariaAcordada); }
  public boolean requiereGarantia() { return garantiaMonetariaAcordada != null && garantiaMonetariaAcordada.signum() > 0; }
  public boolean esPrestatario(UUID usuarioId) { return prestatarioUsuarioId.equals(usuarioId); }
  public boolean esParticipante(UUID usuarioId) { return esPrestatario(usuarioId) || prestamistaUsuarioId.equals(usuarioId); }

  public UUID reservaId() { return reservaId; } public UUID prestamoId() { return prestamoId; }
  public UUID prestamistaUsuarioId() { return prestamistaUsuarioId; } public UUID prestatarioUsuarioId() { return prestatarioUsuarioId; }
  public PeriodoPrestamo periodo() { return periodo; } public BigDecimal tarifaDiariaAcordada() { return tarifaDiariaAcordada; }
  public BigDecimal garantiaMonetariaAcordada() { return garantiaMonetariaAcordada; } public String moneda() { return moneda; }
  public EstadoPrestamoPagable estado() { return estado; } public BigDecimal costoExtensionPendiente() { return costoExtensionPendiente; }
  public UUID cambioFechaPendienteId() { return cambioFechaPendienteId; } public Instant creadoEn() { return creadoEn; } public Instant actualizadoEn() { return actualizadoEn; }
}
