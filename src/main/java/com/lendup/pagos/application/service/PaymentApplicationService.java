package com.lendup.pagos.application.service;

import com.lendup.pagos.application.exception.*;
import com.lendup.pagos.application.port.in.PaymentUseCases;
import com.lendup.pagos.application.port.in.PaymentViews.*;
import com.lendup.pagos.application.port.out.AuditPort;
import com.lendup.pagos.application.port.out.PaymentGatewayPort;
import com.lendup.pagos.application.port.out.PaymentGatewayPort.ProviderPayment;
import com.lendup.pagos.application.port.out.PaymentGatewayPort.RefundResult;
import com.lendup.pagos.application.port.out.TransactionRepository;
import com.lendup.pagos.domain.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Payment Application Service (Fig. 18): pagos de tarifa y extensión, liberación de tarifa, reembolsos y
 * aplicación de resultados del proveedor. También mantiene la proyección prestamos_pagables.
 */
@Service
public class PaymentApplicationService implements PaymentUseCases {
  private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);
  private static final Set<TipoTransaccion> COBROS = EnumSet.of(TipoTransaccion.PAGO_TARIFA, TipoTransaccion.PAGO_EXTENSION, TipoTransaccion.CONSTITUCION_GARANTIA);
  private static final Set<TipoTransaccion> REEMBOLSOS = EnumSet.of(TipoTransaccion.DEVOLUCION_GARANTIA, TipoTransaccion.REEMBOLSO_TARIFA);
  private static final Set<TipoTransaccion> PAGOS_DE_TARIFA = EnumSet.of(TipoTransaccion.PAGO_TARIFA, TipoTransaccion.PAGO_EXTENSION);

  private final TransactionRepository repository;
  private final PaymentGatewayPort gateway;
  private final GuaranteeApplicationService guarantees;
  private final CheckoutCoordinator checkout;
  private final EconomicEventRecorder events;
  private final AuditPort audit;
  private final TransactionOperations tx;
  private final Clock clock;
  private final PoliticaDeCosto politicaDeCosto = PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS;

  public PaymentApplicationService(TransactionRepository repository, PaymentGatewayPort gateway, GuaranteeApplicationService guarantees,
                                   CheckoutCoordinator checkout, EconomicEventRecorder events, AuditPort audit, TransactionOperations tx, Clock clock) {
    this.repository = repository; this.gateway = gateway; this.guarantees = guarantees; this.checkout = checkout; this.events = events;
    this.audit = audit; this.tx = tx; this.clock = clock;
  }

  @Override
  public List<MedioPagoView> listarMediosPago() {
    return List.of(new MedioPagoView(MedioPago.MERCADO_PAGO, MedioPago.NOMBRE_MERCADO_PAGO, "ACTIVO"));
  }

  @Override
  public TransaccionView crearPago(UUID actor, CrearPagoCommand command, String correlationId) {
    TipoTransaccion tipo = tipoDePago(command.tipo());
    if (!MedioPago.MERCADO_PAGO.equals(command.medioPagoSeleccionado())) throw new IllegalArgumentException("Medio de pago inválido");
    TransaccionEconomica registrada = tx.execute(status -> registrarPago(actor, tipo, command, correlationId));
    return TransaccionView.from(checkout.asegurarCheckout(registrada));
  }

  private TransaccionEconomica registrarPago(UUID actor, TipoTransaccion tipo, CrearPagoCommand command, String correlationId) {
    Optional<TransaccionEconomica> repeated = repository.findByClaveIdempotencia(command.claveIdempotencia());
    if (repeated.isPresent()) {
      TransaccionEconomica previous = repeated.get();
      if (!previous.prestamoId().equals(command.prestamoId()) || previous.tipo() != tipo) throw new BusinessConflictException("La clave de pago ya fue utilizada");
      GuaranteeApplicationService.exigirPrestatario(proyeccion(command.prestamoId()), actor);
      return previous;
    }
    PrestamoPagable prestamo = repository.lockProyeccionByPrestamo(command.prestamoId()).orElseThrow(() -> new NotFoundException("Préstamo no encontrado"));
    GuaranteeApplicationService.exigirPrestatario(prestamo, actor);
    BigDecimal monto = dinero(command.monto());
    List<TransaccionEconomica> existentes = repository.findByPrestamo(prestamo.prestamoId());
    UUID cambioFecha = null;
    if (tipo == TipoTransaccion.PAGO_TARIFA) {
      if (prestamo.estado() != EstadoPrestamoPagable.RESERVADO) throw new BusinessConflictException("El préstamo ya no admite pagos");
      if (existentes.stream().anyMatch(t -> t.tipo() == tipo && t.estado() == EstadoTransaccion.CONFIRMADA)) throw new BusinessConflictException("La tarifa ya fue pagada");
      if (monto.compareTo(prestamo.tarifaTotal(politicaDeCosto)) != 0) throw new IllegalArgumentException("El importe no coincide con la tarifa acordada");
    } else {
      if (prestamo.estado() != EstadoPrestamoPagable.ACTIVO) throw new BusinessConflictException("La extensión solo se paga con el préstamo activo");
      if (prestamo.costoExtensionPendiente() == null) throw new BusinessConflictException("No hay una extensión aceptada pendiente de pago");
      if (command.cambioFechaPrestamoId() == null || !command.cambioFechaPrestamoId().equals(prestamo.cambioFechaPendienteId())) throw new IllegalArgumentException("cambio_fecha_prestamo_id no corresponde a la extensión aceptada");
      if (monto.compareTo(prestamo.costoExtensionPendiente()) != 0) throw new IllegalArgumentException("El importe no coincide con el costo de la extensión");
      cambioFecha = command.cambioFechaPrestamoId();
    }
    UUID cambio = cambioFecha;
    Optional<TransaccionEconomica> enCurso = existentes.stream()
        .filter(t -> t.tipo() == tipo && t.estado() == EstadoTransaccion.PENDIENTE && Objects.equals(t.cambioFechaPrestamoId(), cambio)).findFirst();
    if (enCurso.isPresent()) return enCurso.get();
    TransaccionEconomica t = TransaccionEconomica.nueva(prestamo.prestamoId(), null, cambioFecha, null, tipo, monto, prestamo.moneda(),
        MedioPago.MERCADO_PAGO, null, command.claveIdempotencia(), clock.instant());
    repository.saveTransaccion(t);
    audit.record("TRANSACCION", t.id(), "SOLICITAR_" + tipo.name(), null, t.estado().name(), actor, correlationId);
    return t;
  }

  @Override @Transactional(readOnly = true)
  public List<TransaccionView> listarTransacciones(UUID actor, UUID prestamoId) {
    PrestamoPagable prestamo = proyeccion(prestamoId);
    if (!prestamo.esParticipante(actor)) throw new ForbiddenException("No participas en este préstamo");
    return repository.findByPrestamo(prestamoId).stream().map(TransaccionView::from).toList();
  }

  @Override
  public List<UUID> aplicarResultadoCobro(ProviderPayment payment, String correlationId) {
    UUID transaccionId = uuidOrNull(payment.externalReference());
    if (transaccionId == null) {
      log.info("Pago {} del proveedor sin external_reference de LendUp; se ignora", payment.paymentId());
      return List.of();
    }
    List<UUID> reembolsos = tx.execute(status -> aplicar(transaccionId, payment, correlationId));
    return reembolsos == null ? List.of() : reembolsos;
  }

  private List<UUID> aplicar(UUID transaccionId, ProviderPayment payment, String correlationId) {
    TransaccionEconomica t = repository.lockTransaccion(transaccionId).orElse(null);
    if (t == null || !COBROS.contains(t.tipo())) return List.of();
    if (payment.resultado() == ResultadoProveedor.APROBADO && !coincide(t, payment)) {
      log.warn("El pago {} no coincide con el monto o moneda de la transacción {}; no se confirma", payment.paymentId(), t.id());
      audit.record("TRANSACCION", t.id(), "RESULTADO_INCONSISTENTE", t.estado().name(), t.estado().name(), null, correlationId);
      return List.of();
    }
    Instant now = clock.instant();
    String previous = t.estado().name();
    boolean changed = t.aplicarResultadoCobro(payment.resultado(), payment.paymentId(), payment.comision(), now);
    repository.saveTransaccion(t);
    if (!changed) return List.of();
    audit.record("TRANSACCION", t.id(), "RESULTADO_PROVEEDOR", previous, t.estado().name(), null, correlationId);
    PrestamoPagable prestamo = repository.lockProyeccionByPrestamo(t.prestamoId()).orElse(null);
    if (t.estado() != EstadoTransaccion.CONFIRMADA) {
      events.pagoRechazado(t, prestamo, payment.detalle(), correlationId);
      return List.of();
    }
    if (t.tipo() == TipoTransaccion.CONSTITUCION_GARANTIA) return guarantees.confirmarConstitucion(t, prestamo, correlationId);
    boolean duplicado = t.tipo() == TipoTransaccion.PAGO_TARIFA && repository.findByPrestamo(t.prestamoId()).stream()
        .anyMatch(o -> !o.id().equals(t.id()) && o.tipo() == TipoTransaccion.PAGO_TARIFA && o.estado() == EstadoTransaccion.CONFIRMADA);
    if (duplicado || prestamo == null || prestamo.estado() == EstadoPrestamoPagable.CANCELADO) {
      // Pago duplicado o llegado después de cancelar la reserva: se devuelve (RNF13, US41).
      return crearReembolsoTarifa(t, correlationId).map(List::of).orElse(List.of());
    }
    events.pagoConfirmado(t, prestamo, correlationId);
    if (t.tipo() == TipoTransaccion.PAGO_EXTENSION) {
      if (Objects.equals(t.cambioFechaPrestamoId(), prestamo.cambioFechaPendienteId())) {
        prestamo.liquidarExtension(now);
        repository.saveProyeccion(prestamo);
      }
      if (prestamo.estado() == EstadoPrestamoPagable.ACTIVO) liberarTarifa(t, prestamo, correlationId);
    }
    return List.of();
  }

  @Override
  public void ejecutarReembolso(UUID transaccionId, String correlationId) {
    TransaccionEconomica t = repository.findTransaccion(transaccionId).orElse(null);
    if (t == null || !REEMBOLSOS.contains(t.tipo()) || t.estado() != EstadoTransaccion.PENDIENTE) return;
    RefundResult result;
    try {
      result = gateway.reembolsar(t.referenciaProveedor(), t.monto(), t.claveIdempotencia());
    } catch (DependencyUnavailableException e) {
      // RNF14: sin respuesta cierta se conserva PENDIENTE y la conciliación reintenta con la misma clave.
      log.warn("Reembolso {} queda pendiente: {}", t.id(), e.getMessage());
      return;
    }
    tx.executeWithoutResult(status -> registrarResultadoReembolso(transaccionId, result, correlationId));
  }

  private void registrarResultadoReembolso(UUID transaccionId, RefundResult result, String correlationId) {
    TransaccionEconomica t = repository.lockTransaccion(transaccionId).orElseThrow();
    if (t.estado() != EstadoTransaccion.PENDIENTE || result.resultado() == ResultadoProveedor.EN_PROCESO) return;
    Instant now = clock.instant();
    if (result.resultado() != ResultadoProveedor.APROBADO) {
      t.fallar(now);
      repository.saveTransaccion(t);
      audit.record("TRANSACCION", t.id(), "REEMBOLSO_RECHAZADO", EstadoTransaccion.PENDIENTE.name(), t.estado().name(), null, correlationId);
      log.error("Mercado Pago rechazó el reembolso {}: {}. Requiere revisión manual", t.id(), result.detalle());
      return;
    }
    t.confirmar(now);
    repository.saveTransaccion(t);
    audit.record("TRANSACCION", t.id(), "REEMBOLSO_CONFIRMADO", EstadoTransaccion.PENDIENTE.name(), t.estado().name(), null, correlationId);
    PrestamoPagable prestamo = repository.findProyeccionByPrestamo(t.prestamoId()).orElse(null);
    if (t.tipo() == TipoTransaccion.REEMBOLSO_TARIFA) events.reembolsoProcesado(t, prestamo, result.refundId(), correlationId);
    else guarantees.registrarDevolucionConfirmada(t, prestamo, result.refundId(), correlationId);
  }

  @Override
  public int conciliarPendientes(Instant solicitadasAntesDe, int limit) {
    int revisadas = 0;
    for (TransaccionEconomica t : repository.findPendientes(COBROS, solicitadasAntesDe, limit)) {
      if (t.checkoutUrl() == null) continue;
      try {
        List<ProviderPayment> pagos = gateway.buscarPagosPorReferencia(t.id());
        Optional<ProviderPayment> elegido = pagos.stream().filter(p -> p.resultado() == ResultadoProveedor.APROBADO).findFirst()
            .or(() -> pagos.isEmpty() ? Optional.empty() : Optional.of(pagos.getLast()));
        if (elegido.isPresent()) aplicarResultadoCobro(elegido.get(), "conciliacion").forEach(id -> ejecutarReembolso(id, "conciliacion"));
        revisadas++;
      } catch (DependencyUnavailableException e) {
        log.warn("Conciliación interrumpida, Mercado Pago no disponible: {}", e.getMessage());
        return revisadas;
      }
    }
    for (TransaccionEconomica t : repository.findPendientes(REEMBOLSOS, solicitadasAntesDe, limit)) {
      ejecutarReembolso(t.id(), "conciliacion");
      revisadas++;
    }
    return revisadas;
  }

  @Override @Transactional
  public void registrarReservaConfirmada(ReservaConfirmadaCommand c, String correlationId) {
    if (repository.lockProyeccionByReserva(c.reservaId()).isPresent()) return;
    PrestamoPagable prestamo = PrestamoPagable.desdeReservaConfirmada(c.reservaId(), c.prestamistaUsuarioId(), c.prestatarioUsuarioId(),
        new PeriodoPrestamo(c.desde(), c.hasta()), c.tarifaDiariaAcordada(), c.garantiaMonetariaAcordada(), c.moneda(), clock.instant());
    repository.saveProyeccion(prestamo);
    audit.record("PRESTAMO_PAGABLE", c.reservaId(), "REGISTRAR_RESERVA", null, prestamo.estado().name(), null, correlationId);
  }

  @Override @Transactional
  public void vincularPrestamo(UUID reservaId, UUID prestamoId, String correlationId) {
    PrestamoPagable prestamo = repository.lockProyeccionByReserva(reservaId).orElseThrow(() -> new NotFoundException("Reserva no proyectada"));
    prestamo.vincularPrestamo(prestamoId, clock.instant());
    repository.saveProyeccion(prestamo);
    audit.record("PRESTAMO_PAGABLE", reservaId, "VINCULAR_PRESTAMO", prestamo.estado().name(), prestamo.estado().name(), null, correlationId);
  }

  @Override @Transactional
  public void registrarPrestamoActivado(UUID prestamoId, String correlationId) {
    PrestamoPagable prestamo = repository.lockProyeccionByPrestamo(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no proyectado"));
    EstadoPrestamoPagable previous = prestamo.estado();
    prestamo.activar(clock.instant());
    repository.saveProyeccion(prestamo);
    audit.record("PRESTAMO_PAGABLE", prestamo.reservaId(), "ACTIVAR", previous.name(), prestamo.estado().name(), null, correlationId);
    // RF40/US35: con el préstamo activo se registra la liberación de la tarifa pagada al prestamista.
    for (TransaccionEconomica pago : repository.findByPrestamo(prestamoId)) {
      if (PAGOS_DE_TARIFA.contains(pago.tipo()) && pago.estado() == EstadoTransaccion.CONFIRMADA) liberarTarifa(pago, prestamo, correlationId);
    }
  }

  @Override @Transactional
  public void registrarExtensionAceptada(UUID prestamoId, UUID cambioFechaPrestamoId, BigDecimal costoAdicional, String correlationId) {
    PrestamoPagable prestamo = repository.lockProyeccionByPrestamo(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no proyectado"));
    prestamo.registrarExtensionAceptada(cambioFechaPrestamoId, dinero(costoAdicional), clock.instant());
    repository.saveProyeccion(prestamo);
    audit.record("PRESTAMO_PAGABLE", prestamo.reservaId(), "REGISTRAR_EXTENSION", prestamo.estado().name(), prestamo.estado().name(), null, correlationId);
  }

  @Override @Transactional
  public List<UUID> registrarReservaCancelada(UUID reservaId, String correlationId) {
    PrestamoPagable prestamo = repository.lockProyeccionByReserva(reservaId).orElse(null);
    if (prestamo == null) return List.of();
    EstadoPrestamoPagable previous = prestamo.estado();
    Instant now = clock.instant();
    prestamo.cancelar(now);
    repository.saveProyeccion(prestamo);
    audit.record("PRESTAMO_PAGABLE", reservaId, "CANCELAR", previous.name(), prestamo.estado().name(), null, correlationId);
    if (prestamo.prestamoId() == null) return List.of();
    List<UUID> reembolsos = new ArrayList<>();
    for (TransaccionEconomica t : repository.findByPrestamo(prestamo.prestamoId())) {
      if (!PAGOS_DE_TARIFA.contains(t.tipo())) continue;
      if (t.estado() == EstadoTransaccion.CONFIRMADA) crearReembolsoTarifa(t, correlationId).ifPresent(reembolsos::add);
      else if (t.estado() == EstadoTransaccion.PENDIENTE) {
        t.cancelar(now);
        repository.saveTransaccion(t);
        audit.record("TRANSACCION", t.id(), "CANCELAR", EstadoTransaccion.PENDIENTE.name(), t.estado().name(), null, correlationId);
      }
    }
    reembolsos.addAll(guarantees.devolverPorCancelacion(prestamo, correlationId));
    return reembolsos;
  }

  private void liberarTarifa(TransaccionEconomica pago, PrestamoPagable prestamo, String correlationId) {
    String clave = "liberacion:" + pago.id();
    if (repository.findByClaveIdempotencia(clave).isPresent()) return;
    Instant now = clock.instant();
    TransaccionEconomica liberacion = TransaccionEconomica.nueva(pago.prestamoId(), null, pago.cambioFechaPrestamoId(), null,
        TipoTransaccion.LIBERACION_TARIFA, pago.monto(), pago.moneda(), null, pago.referenciaProveedor(), clave, now);
    // El pago real al prestamista requiere el modo marketplace (ADR 0001): aquí se registra la liberación contable.
    liberacion.confirmar(now);
    repository.saveTransaccion(liberacion);
    audit.record("TRANSACCION", liberacion.id(), "LIBERAR_TARIFA", null, liberacion.estado().name(), null, correlationId);
    events.tarifaLiberada(liberacion, pago, prestamo, correlationId);
  }

  private Optional<UUID> crearReembolsoTarifa(TransaccionEconomica pago, String correlationId) {
    String clave = "reembolso:" + pago.id();
    if (repository.findByClaveIdempotencia(clave).isPresent()) return Optional.empty();
    TransaccionEconomica reembolso = TransaccionEconomica.nueva(pago.prestamoId(), null, pago.cambioFechaPrestamoId(), null,
        TipoTransaccion.REEMBOLSO_TARIFA, pago.monto(), pago.moneda(), null, pago.referenciaProveedor(), clave, clock.instant());
    repository.saveTransaccion(reembolso);
    audit.record("TRANSACCION", reembolso.id(), "SOLICITAR_REEMBOLSO_TARIFA", null, reembolso.estado().name(), null, correlationId);
    return Optional.of(reembolso.id());
  }

  private PrestamoPagable proyeccion(UUID prestamoId) {
    return repository.findProyeccionByPrestamo(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no encontrado"));
  }

  private static TipoTransaccion tipoDePago(String tipo) {
    if ("PAGO_TARIFA".equals(tipo)) return TipoTransaccion.PAGO_TARIFA;
    if ("PAGO_EXTENSION".equals(tipo)) return TipoTransaccion.PAGO_EXTENSION;
    throw new IllegalArgumentException("Tipo de pago inválido");
  }

  private static BigDecimal dinero(BigDecimal value) {
    if (value == null || value.signum() <= 0) throw new IllegalArgumentException("El monto debe ser positivo");
    try { return value.setScale(2, RoundingMode.UNNECESSARY); }
    catch (ArithmeticException e) { throw new IllegalArgumentException("El monto admite como máximo 2 decimales"); }
  }

  private static boolean coincide(TransaccionEconomica t, ProviderPayment payment) {
    boolean monto = payment.monto() == null || payment.monto().compareTo(t.monto()) == 0;
    boolean moneda = payment.moneda() == null || payment.moneda().equals(t.moneda());
    return monto && moneda;
  }

  private static UUID uuidOrNull(String value) {
    try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
  }
}
