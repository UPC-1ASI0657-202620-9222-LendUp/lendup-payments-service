package com.lendup.pagos.infrastructure.scheduler;

import com.lendup.pagos.application.port.in.PaymentUseCases;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Respaldo del webhook (CD-09): consulta en Mercado Pago los cobros PENDIENTE antiguos y reintenta los reembolsos
 * pendientes con su misma clave de idempotencia. Ante timeout conserva el último estado (RNF14).
 */
@Component
public class PendingReconciliationScheduler {
  private static final Logger log = LoggerFactory.getLogger(PendingReconciliationScheduler.class);
  private final PaymentUseCases payments;
  private final Clock clock;
  private final Duration minAge;

  public PendingReconciliationScheduler(PaymentUseCases payments, Clock clock, @Value("${lendup.reconciliation.min-age-ms:600000}") long minAgeMs) {
    this.payments = payments; this.clock = clock; this.minAge = Duration.ofMillis(minAgeMs);
  }

  @Scheduled(fixedDelayString = "${lendup.reconciliation.delay-ms:300000}", initialDelayString = "${lendup.reconciliation.delay-ms:300000}")
  public void reconcile() {
    try {
      int reviewed = payments.conciliarPendientes(clock.instant().minus(minAge), 50);
      if (reviewed > 0) log.info("Conciliación revisó {} transacciones pendientes", reviewed);
    } catch (RuntimeException e) {
      log.warn("Conciliación fallida; se reintentará en la siguiente ejecución: {}", e.getMessage());
    }
  }
}
