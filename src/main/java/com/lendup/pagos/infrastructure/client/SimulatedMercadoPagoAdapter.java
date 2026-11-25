package com.lendup.pagos.infrastructure.client;

import com.lendup.pagos.application.port.out.PaymentGatewayPort;
import com.lendup.pagos.domain.model.ResultadoProveedor;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mercado Pago SIMULADO, solo para desarrollo y pruebas del equipo sin credenciales (MERCADOPAGO_MODE=simulado).
 * El checkout_url apunta al simulador del propio servicio; los pagos y reembolsos viven en memoria. Nunca usar en
 * producción: con MERCADOPAGO_MODE=real (valor por defecto) este adaptador no existe.
 */
@Component
@ConditionalOnProperty(name = "lendup.integrations.mercado-pago.mode", havingValue = "simulado")
public class SimulatedMercadoPagoAdapter implements PaymentGatewayPort {
  private static final Logger log = LoggerFactory.getLogger(SimulatedMercadoPagoAdapter.class);
  public static final String CHECKOUT_PATH = "/api/v1/simulador/mercado-pago/checkout/";
  private final String baseUrl;
  private final Map<String, ProviderPayment> payments = new ConcurrentHashMap<>();
  private final Map<String, RefundResult> refunds = new ConcurrentHashMap<>();
  private final AtomicLong nextId = new AtomicLong(System.currentTimeMillis());

  public SimulatedMercadoPagoAdapter(@Value("${lendup.integrations.mercado-pago.simulator-base-url:http://localhost:8082}") String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    log.warn("Mercado Pago en modo SIMULADO: no se cobra dinero real. No usar en producción.");
  }

  @Override public String crearCheckout(CheckoutRequest request) { return baseUrl + CHECKOUT_PATH + request.transaccionId(); }

  @Override public Optional<ProviderPayment> consultarPago(String paymentId) { return Optional.ofNullable(payments.get(paymentId)); }

  @Override public List<ProviderPayment> buscarPagosPorReferencia(UUID transaccionId) {
    return payments.values().stream().filter(p -> transaccionId.toString().equals(p.externalReference())).toList();
  }

  @Override public RefundResult reembolsar(String paymentId, BigDecimal monto, String idempotencyKey) {
    // Igual que X-Idempotency-Key en Mercado Pago: la misma clave devuelve el mismo reembolso.
    return refunds.computeIfAbsent(idempotencyKey, key -> new RefundResult("sim-refund-" + nextId.incrementAndGet(), ResultadoProveedor.APROBADO, "approved"));
  }

  /** Simula que el comprador terminó el checkout: registra un pago con external_reference = id de la transacción. */
  public ProviderPayment registrarPago(UUID transaccionId, boolean aprobado, BigDecimal monto, String moneda) {
    String id = String.valueOf(nextId.incrementAndGet());
    ProviderPayment payment = new ProviderPayment(id, transaccionId.toString(), aprobado ? ResultadoProveedor.APROBADO : ResultadoProveedor.RECHAZADO,
        aprobado ? "accredited" : "cc_rejected_other_reason", monto, moneda, BigDecimal.ZERO);
    payments.put(id, payment);
    return payment;
  }
}
