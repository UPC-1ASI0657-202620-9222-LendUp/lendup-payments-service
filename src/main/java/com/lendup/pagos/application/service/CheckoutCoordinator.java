package com.lendup.pagos.application.service;

import com.lendup.pagos.application.port.out.PaymentGatewayPort;
import com.lendup.pagos.application.port.out.PaymentGatewayPort.CheckoutRequest;
import com.lendup.pagos.application.port.out.TransactionRepository;
import com.lendup.pagos.domain.model.EstadoTransaccion;
import com.lendup.pagos.domain.model.TransaccionEconomica;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Crea la preferencia de Checkout Pro fuera de la transacción de base de datos (no se mantiene un bloqueo durante
 * la llamada HTTP) y guarda la URL después. Si el proveedor falla, la transacción queda PENDIENTE sin URL y un
 * reintento con la misma clave de idempotencia vuelve a intentarlo.
 */
@Component
public class CheckoutCoordinator {
  private final TransactionRepository repository;
  private final PaymentGatewayPort gateway;
  private final TransactionOperations tx;
  private final Clock clock;

  public CheckoutCoordinator(TransactionRepository repository, PaymentGatewayPort gateway, TransactionOperations tx, Clock clock) {
    this.repository = repository; this.gateway = gateway; this.tx = tx; this.clock = clock;
  }

  public TransaccionEconomica asegurarCheckout(TransaccionEconomica t) {
    if (t.estado() != EstadoTransaccion.PENDIENTE || t.checkoutUrl() != null) return t;
    String url = gateway.crearCheckout(new CheckoutRequest(t.id(), t.prestamoId(), titulo(t), t.monto(), t.moneda()));
    return tx.execute(status -> {
      TransaccionEconomica locked = repository.lockTransaccion(t.id()).orElseThrow();
      if (locked.estado() != EstadoTransaccion.PENDIENTE || locked.checkoutUrl() != null) return locked;
      locked.asignarCheckout(url, clock.instant());
      return repository.saveTransaccion(locked);
    });
  }

  private String titulo(TransaccionEconomica t) {
    return switch (t.tipo()) {
      case PAGO_EXTENSION -> "LendUp - Extensión del préstamo";
      case CONSTITUCION_GARANTIA -> "LendUp - Garantía monetaria";
      default -> "LendUp - Tarifa del préstamo";
    };
  }
}
