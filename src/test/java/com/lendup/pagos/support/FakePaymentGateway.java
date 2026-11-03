package com.lendup.pagos.support;

import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.port.out.PaymentGatewayPort;
import com.lendup.pagos.domain.model.ResultadoProveedor;
import java.math.BigDecimal;
import java.util.*;

/** Adaptador falso de Mercado Pago, solo para pruebas: registra llamadas y permite simular caídas. */
public class FakePaymentGateway implements PaymentGatewayPort {
  public final Map<String, ProviderPayment> pagos = new HashMap<>();
  public final List<String> refundKeys = new ArrayList<>();
  public final Map<String, BigDecimal> refundedAmounts = new LinkedHashMap<>();
  public boolean unavailable;
  public ResultadoProveedor refundResult = ResultadoProveedor.APROBADO;
  private long nextPaymentId = 1000;

  @Override public String crearCheckout(CheckoutRequest request) {
    failIfUnavailable();
    return "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=test-" + request.transaccionId();
  }
  @Override public Optional<ProviderPayment> consultarPago(String paymentId) { failIfUnavailable(); return Optional.ofNullable(pagos.get(paymentId)); }
  @Override public List<ProviderPayment> buscarPagosPorReferencia(UUID transaccionId) {
    failIfUnavailable();
    return pagos.values().stream().filter(p -> transaccionId.toString().equals(p.externalReference())).toList();
  }
  @Override public RefundResult reembolsar(String paymentId, BigDecimal monto, String idempotencyKey) {
    failIfUnavailable();
    refundKeys.add(idempotencyKey);
    if (refundResult == ResultadoProveedor.APROBADO) refundedAmounts.merge(paymentId, monto, BigDecimal::add);
    return new RefundResult("refund-" + refundKeys.size(), refundResult, refundResult.name());
  }

  /** Simula que el prestatario pagó en Checkout Pro: el proveedor conoce un pago con esa external_reference. */
  public ProviderPayment registrarPago(UUID transaccionId, ResultadoProveedor resultado, BigDecimal monto) {
    String id = String.valueOf(nextPaymentId++);
    ProviderPayment payment = new ProviderPayment(id, transaccionId.toString(), resultado, resultado == ResultadoProveedor.RECHAZADO ? "cc_rejected_insufficient_amount" : "accredited",
        monto, "PEN", new BigDecimal("1.20"));
    pagos.put(id, payment);
    return payment;
  }

  private void failIfUnavailable() { if (unavailable) throw new DependencyUnavailableException("Mercado Pago no respondió (simulado)"); }
}
