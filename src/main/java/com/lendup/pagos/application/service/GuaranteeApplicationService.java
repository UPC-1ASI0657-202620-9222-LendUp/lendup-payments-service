package com.lendup.pagos.application.service;

import com.lendup.pagos.application.exception.*;
import com.lendup.pagos.application.port.in.GuaranteeUseCases;
import com.lendup.pagos.application.port.in.PaymentViews.*;
import com.lendup.pagos.application.port.out.AuditPort;
import com.lendup.pagos.application.port.out.TransactionRepository;
import com.lendup.pagos.domain.model.*;
import com.lendup.pagos.domain.service.LiquidacionDeGarantiaService;
import com.lendup.pagos.domain.service.LiquidacionDeGarantiaService.Liquidacion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/** Guarantee Application Service (Fig. 18): constitución, retención y devolución de la garantía monetaria. */
@Service
public class GuaranteeApplicationService implements GuaranteeUseCases {
  private static final Logger log = LoggerFactory.getLogger(GuaranteeApplicationService.class);
  /** Prefijo de los reembolsos de un pago de garantía que llegó cuando ya no correspondía (no afecta el saldo). */
  static final String SURPLUS_REFUND_PREFIX = "devolucion-pago:";

  private final TransactionRepository repository;
  private final CheckoutCoordinator checkout;
  private final EconomicEventRecorder events;
  private final AuditPort audit;
  private final TransactionOperations tx;
  private final Clock clock;
  private final LiquidacionDeGarantiaService liquidacion = new LiquidacionDeGarantiaService();

  public GuaranteeApplicationService(TransactionRepository repository, CheckoutCoordinator checkout, EconomicEventRecorder events,
                                     AuditPort audit, TransactionOperations tx, Clock clock) {
    this.repository = repository; this.checkout = checkout; this.events = events; this.audit = audit; this.tx = tx; this.clock = clock;
  }

  @Override
  public TransaccionView constituirGarantia(UUID actor, ConstituirGarantiaCommand command, String correlationId) {
    String medio = command.medioPagoSeleccionado() == null || command.medioPagoSeleccionado().isBlank()
        ? MedioPago.MERCADO_PAGO : command.medioPagoSeleccionado();
    if (!MedioPago.MERCADO_PAGO.equals(medio)) throw new IllegalArgumentException("Medio de pago inválido");
    String clave = command.claveIdempotencia() == null || command.claveIdempotencia().isBlank()
        ? "garantia-" + UUID.randomUUID() : command.claveIdempotencia();
    TransaccionEconomica registrada = tx.execute(status -> registrar(actor, command.prestamoId(), medio, clave, correlationId));
    TransaccionEconomica conCheckout = checkout.asegurarCheckout(registrada);
    Garantia garantia = repository.findGarantiaByPrestamo(conCheckout.prestamoId()).orElse(null);
    return TransaccionView.from(conCheckout, garantia);
  }

  private TransaccionEconomica registrar(UUID actor, UUID prestamoId, String medio, String clave, String correlationId) {
    Optional<TransaccionEconomica> repeated = repository.findByClaveIdempotencia(clave);
    if (repeated.isPresent()) {
      TransaccionEconomica previous = repeated.get();
      if (!previous.prestamoId().equals(prestamoId) || previous.tipo() != TipoTransaccion.CONSTITUCION_GARANTIA) {
        throw new BusinessConflictException("La clave de pago ya fue utilizada");
      }
      exigirPrestatario(repository.findProyeccionByPrestamo(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no encontrado")), actor);
      return previous;
    }
    PrestamoPagable prestamo = repository.lockProyeccionByPrestamo(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no encontrado"));
    exigirPrestatario(prestamo, actor);
    if (prestamo.estado() != EstadoPrestamoPagable.RESERVADO) throw new BusinessConflictException("El préstamo ya no admite garantías");
    if (!prestamo.requiereGarantia()) throw new BusinessConflictException("El préstamo no requiere garantía");
    Instant now = clock.instant();
    Garantia garantia = repository.findGarantiaByPrestamo(prestamoId).orElse(null);
    if (garantia != null) {
      if (garantia.estado() != EstadoGarantia.PENDIENTE) throw new BusinessConflictException("Garantía ya registrada");
      Optional<TransaccionEconomica> enCurso = repository.findByPrestamo(prestamoId).stream()
          .filter(t -> t.tipo() == TipoTransaccion.CONSTITUCION_GARANTIA && t.estado() == EstadoTransaccion.PENDIENTE).findFirst();
      if (enCurso.isPresent()) return enCurso.get();
    } else {
      garantia = repository.saveGarantia(Garantia.nueva(prestamoId, prestamo.garantiaMonetariaAcordada(), prestamo.moneda(), now));
      audit.record("GARANTIA", garantia.id(), "REGISTRAR", null, garantia.estado().name(), actor, correlationId);
    }
    TransaccionEconomica t = TransaccionEconomica.nueva(prestamoId, garantia.id(), null, null, TipoTransaccion.CONSTITUCION_GARANTIA,
        garantia.montoAcordado(), garantia.moneda(), medio, null, clave, now);
    repository.saveTransaccion(t);
    audit.record("TRANSACCION", t.id(), "SOLICITAR_CONSTITUCION_GARANTIA", null, t.estado().name(), actor, correlationId);
    return t;
  }

  /** Se ejecuta dentro de la transacción que confirma el cobro (webhook o conciliación). */
  public List<UUID> confirmarConstitucion(TransaccionEconomica pago, PrestamoPagable prestamo, String correlationId) {
    Garantia garantia = repository.lockGarantia(pago.garantiaId()).orElseThrow(() -> new NotFoundException("Garantía no encontrada"));
    if (garantia.estado() != EstadoGarantia.PENDIENTE) {
      // El prestatario pagó cuando la garantía ya estaba constituida o cancelada: se devuelve ese pago completo.
      log.warn("Pago de garantía {} recibido con la garantía en estado {}; se reembolsa", pago.id(), garantia.estado());
      return crearDevolucion(garantia, pago.monto(), pago.referenciaProveedor(), null, SURPLUS_REFUND_PREFIX + pago.id(), correlationId);
    }
    Instant now = clock.instant();
    garantia.constituir(pago.monto(), now);
    repository.saveGarantia(garantia);
    audit.record("GARANTIA", garantia.id(), "CONSTITUIR", EstadoGarantia.PENDIENTE.name(), garantia.estado().name(), null, correlationId);
    events.garantiaConstituida(garantia, pago, prestamo, correlationId);
    if (prestamo != null && prestamo.estado() == EstadoPrestamoPagable.CANCELADO) {
      return devolverSaldo(garantia, "cancelacion", null, correlationId);
    }
    return List.of();
  }

  /** Reserva cancelada antes de la entrega: cancela la garantía pendiente o devuelve la constituida (US41). */
  public List<UUID> devolverPorCancelacion(PrestamoPagable prestamo, String correlationId) {
    Optional<Garantia> found = repository.findGarantiaByPrestamo(prestamo.prestamoId());
    if (found.isEmpty()) return List.of();
    Garantia garantia = repository.lockGarantia(found.get().id()).orElseThrow();
    Instant now = clock.instant();
    if (garantia.estado() == EstadoGarantia.PENDIENTE) {
      garantia.cancelar(now);
      repository.saveGarantia(garantia);
      audit.record("GARANTIA", garantia.id(), "CANCELAR", EstadoGarantia.PENDIENTE.name(), garantia.estado().name(), null, correlationId);
      for (TransaccionEconomica t : repository.findByPrestamo(prestamo.prestamoId())) {
        if (t.tipo() == TipoTransaccion.CONSTITUCION_GARANTIA && t.estado() == EstadoTransaccion.PENDIENTE) {
          t.cancelar(now);
          repository.saveTransaccion(t);
          audit.record("TRANSACCION", t.id(), "CANCELAR", EstadoTransaccion.PENDIENTE.name(), t.estado().name(), null, correlationId);
        }
      }
      return List.of();
    }
    return devolverSaldo(garantia, "cancelacion", null, correlationId);
  }

  @Override @Transactional
  public List<UUID> registrarPrestamoFinalizado(UUID prestamoId, String correlationId) {
    PrestamoPagable prestamo = repository.lockProyeccionByPrestamo(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no proyectado"));
    EstadoPrestamoPagable previous = prestamo.estado();
    prestamo.finalizar(clock.instant());
    repository.saveProyeccion(prestamo);
    audit.record("PRESTAMO_PAGABLE", prestamo.reservaId(), "FINALIZAR", previous.name(), prestamo.estado().name(), null, correlationId);
    Optional<Garantia> found = repository.findGarantiaByPrestamo(prestamoId);
    if (found.isEmpty()) return List.of();
    Garantia garantia = repository.lockGarantia(found.get().id()).orElseThrow();
    if (!garantia.constituida()) return List.of();
    return devolverSaldo(garantia, "finalizacion", null, correlationId);
  }

  @Override @Transactional
  public List<UUID> registrarIncidenciaResuelta(IncidenciaResueltaCommand command, String correlationId) {
    PrestamoPagable prestamo = repository.findProyeccionByPrestamo(command.prestamoId()).orElseThrow(() -> new NotFoundException("Préstamo no proyectado"));
    Optional<Garantia> found = repository.findGarantiaByPrestamo(command.prestamoId());
    if (found.isEmpty() || !found.get().constituida()) {
      log.info("Incidencia {} resuelta sin garantía constituida en el préstamo {}", command.incidenciaId(), command.prestamoId());
      return List.of();
    }
    Garantia garantia = repository.lockGarantia(found.get().id()).orElseThrow();
    Liquidacion resultado = liquidacion.liquidar(garantia, command.decision(), command.montoAfectado());
    String claveRetencion = "retencion:" + command.incidenciaId();
    if (resultado.retencion().signum() > 0 && repository.findByClaveIdempotencia(claveRetencion).isEmpty()) {
      Instant now = clock.instant();
      TransaccionEconomica retencion = TransaccionEconomica.nueva(garantia.prestamoId(), garantia.id(), null, command.incidenciaId(),
          TipoTransaccion.RETENCION_GARANTIA, resultado.retencion(), garantia.moneda(), null, referenciaConstitucion(garantia), claveRetencion, now);
      retencion.confirmar(now);
      repository.saveTransaccion(retencion);
      String previous = garantia.estado().name();
      garantia.aplicarAfectacion(resultado.retencion(), now);
      repository.saveGarantia(garantia);
      audit.record("TRANSACCION", retencion.id(), "REGISTRAR_RETENCION", null, retencion.estado().name(), null, correlationId);
      audit.record("GARANTIA", garantia.id(), "RETENER_" + command.decision().name(), previous, garantia.estado().name(), null, correlationId);
      events.garantiaRetenida(garantia, retencion, prestamo, command.decision(), correlationId);
    }
    if (resultado.devolucion().signum() <= 0) return List.of();
    return devolverSaldo(garantia, "incidencia", command.incidenciaId(), correlationId);
  }

  /** Se ejecuta dentro de la transacción que confirma el reembolso en el proveedor. */
  public void registrarDevolucionConfirmada(TransaccionEconomica devolucion, PrestamoPagable prestamo, String referenciaReembolso, String correlationId) {
    Garantia garantia = repository.lockGarantia(devolucion.garantiaId()).orElseThrow(() -> new NotFoundException("Garantía no encontrada"));
    if (!devolucion.claveIdempotencia().startsWith(SURPLUS_REFUND_PREFIX) && garantia.constituida()) {
      String previous = garantia.estado().name();
      garantia.registrarDevolucion(devolucion.monto(), clock.instant());
      repository.saveGarantia(garantia);
      audit.record("GARANTIA", garantia.id(), "REGISTRAR_DEVOLUCION", previous, garantia.estado().name(), null, correlationId);
    }
    events.garantiaDevuelta(garantia, devolucion, prestamo, referenciaReembolso, correlationId);
  }

  /** Devuelve el saldo disponible menos lo que ya está en trámite; idempotente por motivo. */
  private List<UUID> devolverSaldo(Garantia garantia, String motivo, UUID incidenciaId, String correlationId) {
    BigDecimal enTramite = repository.sumMontoPendiente(garantia.id(), TipoTransaccion.DEVOLUCION_GARANTIA);
    BigDecimal monto = garantia.saldoDisponible().subtract(enTramite == null ? BigDecimal.ZERO : enTramite);
    if (monto.signum() <= 0) return List.of();
    String clave = "devolucion-garantia:" + garantia.id() + ":" + motivo + (incidenciaId == null ? "" : ":" + incidenciaId);
    return crearDevolucion(garantia, monto, referenciaConstitucion(garantia), incidenciaId, clave, correlationId);
  }

  private List<UUID> crearDevolucion(Garantia garantia, BigDecimal monto, String referenciaPago, UUID incidenciaId, String clave, String correlationId) {
    if (repository.findByClaveIdempotencia(clave).isPresent()) return List.of();
    if (referenciaPago == null || referenciaPago.isBlank()) {
      log.error("No existe referencia del pago de la garantía {}; la devolución requiere revisión manual", garantia.id());
      return List.of();
    }
    TransaccionEconomica devolucion = TransaccionEconomica.nueva(garantia.prestamoId(), garantia.id(), null, incidenciaId,
        TipoTransaccion.DEVOLUCION_GARANTIA, monto, garantia.moneda(), null, referenciaPago, clave, clock.instant());
    repository.saveTransaccion(devolucion);
    audit.record("TRANSACCION", devolucion.id(), "SOLICITAR_DEVOLUCION_GARANTIA", null, devolucion.estado().name(), null, correlationId);
    return List.of(devolucion.id());
  }

  private String referenciaConstitucion(Garantia garantia) {
    return repository.findByPrestamo(garantia.prestamoId()).stream()
        .filter(t -> t.tipo() == TipoTransaccion.CONSTITUCION_GARANTIA && t.estado() == EstadoTransaccion.CONFIRMADA && garantia.id().equals(t.garantiaId()))
        .map(TransaccionEconomica::referenciaProveedor).filter(Objects::nonNull).findFirst().orElse(null);
  }

  static void exigirPrestatario(PrestamoPagable prestamo, UUID actor) {
    if (!prestamo.esParticipante(actor)) throw new ForbiddenException("No participas en este préstamo");
    if (!prestamo.esPrestatario(actor)) throw new ForbiddenException("Operación no permitida");
  }
}
