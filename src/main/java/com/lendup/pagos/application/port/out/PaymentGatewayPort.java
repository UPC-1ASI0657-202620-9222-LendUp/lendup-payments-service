package com.lendup.pagos.application.port.out;

import com.lendup.pagos.domain.model.ResultadoProveedor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto hacia el proveedor de pagos (PasarelaPagosPort en el informe). El adaptador de Mercado Pago es la capa
 * anticorrupción: traduce estados del proveedor a {@link ResultadoProveedor}. Ante timeout, error 5xx o circuito
 * abierto lanza DependencyUnavailableException y el llamador conserva el último estado confirmado (RNF14).
 */
public interface PaymentGatewayPort {
  /** Crea una preferencia de Checkout Pro con external_reference = id de la transacción y devuelve la URL de pago. */
  String crearCheckout(CheckoutRequest request);

  /** Consulta un pago por su id del proveedor (fuente de verdad). Vacío si el proveedor no lo conoce. */
  Optional<ProviderPayment> consultarPago(String paymentId);

  /** Busca los pagos asociados a una transacción (conciliación de pendientes). */
  List<ProviderPayment> buscarPagosPorReferencia(UUID transaccionId);

  /** Reembolso total o parcial; la clave de idempotencia evita duplicados en reintentos. */
  RefundResult reembolsar(String paymentId, BigDecimal monto, String idempotencyKey);

  record CheckoutRequest(UUID transaccionId, UUID prestamoId, String titulo, BigDecimal monto, String moneda) {}

  record ProviderPayment(String paymentId, String externalReference, ResultadoProveedor resultado, String detalle,
                         BigDecimal monto, String moneda, BigDecimal comision) {}

  record RefundResult(String refundId, ResultadoProveedor resultado, String detalle) {}
}
