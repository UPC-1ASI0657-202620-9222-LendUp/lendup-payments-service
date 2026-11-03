package com.lendup.pagos.support;

import com.lendup.pagos.application.port.in.PaymentViews.ReservaConfirmadaCommand;
import com.lendup.pagos.application.port.out.AuditPort;
import com.lendup.pagos.application.port.out.OutboxPort;
import com.lendup.pagos.application.service.*;
import com.lendup.pagos.domain.event.EventEnvelope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.transaction.support.TransactionOperations;

/** Arma los servicios de aplicación con adaptadores en memoria y el adaptador falso de Mercado Pago. */
public class PaymentsTestFixture {
  public final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
  public final FakePaymentGateway gateway = new FakePaymentGateway();
  public final List<EventEnvelope> events = new ArrayList<>();
  public final List<String> auditActions = new ArrayList<>();
  public final Clock clock = Clock.systemUTC();
  public final GuaranteeApplicationService guarantees;
  public final PaymentApplicationService payments;

  public final UUID prestamista = UUID.randomUUID();
  public final UUID prestatario = UUID.randomUUID();

  public PaymentsTestFixture() {
    OutboxPort outbox = events::add;
    AuditPort audit = (type, id, action, prev, next, actor, corr) -> auditActions.add(type + ":" + action + ":" + next);
    TransactionOperations tx = TransactionOperations.withoutTransaction();
    EconomicEventRecorder recorder = new EconomicEventRecorder(outbox, clock);
    CheckoutCoordinator checkout = new CheckoutCoordinator(repository, gateway, tx, clock);
    guarantees = new GuaranteeApplicationService(repository, checkout, recorder, audit, tx, clock);
    payments = new PaymentApplicationService(repository, gateway, guarantees, checkout, recorder, audit, tx, clock);
  }

  /** Simula reservas.confirmada.v1 + el evento de Préstamos que asigna el prestamo_id. Periodo de 48 h + 1 s. */
  public UUID prestamoReservado(BigDecimal tarifaDiaria, BigDecimal garantia) { return prestamoReservado(tarifaDiaria, garantia, 3); }

  /** El periodo dura (bloques - 1) × 24 h + 1 s, de modo que se inician exactamente {@code bloques} bloques. */
  public UUID prestamoReservado(BigDecimal tarifaDiaria, BigDecimal garantia, int bloques) {
    UUID reserva = UUID.randomUUID(), prestamo = UUID.randomUUID();
    LocalDateTime desde = LocalDateTime.of(2026, 11, 10, 9, 0);
    LocalDateTime hasta = desde.plusHours(24L * (bloques - 1)).plusSeconds(1);
    payments.registrarReservaConfirmada(new ReservaConfirmadaCommand(reserva, prestamista, prestatario, desde, hasta, tarifaDiaria, garantia, "PEN"), "corr");
    payments.vincularPrestamo(reserva, prestamo, "corr");
    return prestamo;
  }

  public UUID reservaDe(UUID prestamoId) { return repository.findProyeccionByPrestamo(prestamoId).orElseThrow().reservaId(); }

  public List<String> eventTypes() { return events.stream().map(EventEnvelope::eventType).toList(); }
}
