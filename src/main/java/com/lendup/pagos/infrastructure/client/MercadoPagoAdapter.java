package com.lendup.pagos.infrastructure.client;

import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.port.out.PaymentGatewayPort;
import com.lendup.pagos.config.IntegrationProperties;
import com.lendup.pagos.domain.model.ResultadoProveedor;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentFeeDetail;
import com.mercadopago.resources.payment.PaymentRefund;
import com.mercadopago.resources.preference.Preference;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Mercado Pago Adapter (Fig. 18): capa anticorrupción sobre el SDK oficial. Traduce estados del proveedor a
 * {@link ResultadoProveedor}. Timeout por llamada (connect/socket) y circuit breaker de Resilience4j: ante falla o
 * circuito abierto se lanza DependencyUnavailableException y no se asume éxito ni rechazo (RNF14).
 */
@Component
public class MercadoPagoAdapter implements PaymentGatewayPort {
  private final IntegrationProperties.MercadoPago config;
  private final PreferenceClient preferences;
  private final PaymentClient payments;
  private final PaymentRefundClient refunds;
  private final CircuitBreaker circuitBreaker;

  public MercadoPagoAdapter(IntegrationProperties properties) {
    this.config = properties.mercadoPago();
    BaseUrlHttpClient http = new BaseUrlHttpClient(config.apiBaseUrl());
    this.preferences = new PreferenceClient(http);
    this.payments = new PaymentClient(http);
    this.refunds = new PaymentRefundClient(http);
    this.circuitBreaker = CircuitBreaker.of("mercadoPago", CircuitBreakerConfig.custom()
        .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
        .slowCallDurationThreshold(Duration.ofMillis(config.readTimeoutMs())).slowCallRateThreshold(80)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .recordExceptions(DependencyUnavailableException.class)
        .build());
  }

  @Override
  public String crearCheckout(CheckoutRequest request) {
    requireConfigured();
    PreferenceRequest.PreferenceRequestBuilder builder = PreferenceRequest.builder()
        .items(List.of(PreferenceItemRequest.builder().id(request.transaccionId().toString()).title(request.titulo())
            .quantity(1).currencyId(request.moneda()).unitPrice(request.monto()).build()))
        .externalReference(request.transaccionId().toString())
        .statementDescriptor("LENDUP")
        .metadata(Map.<String, Object>of("prestamo_id", request.prestamoId().toString(), "transaccion_id", request.transaccionId().toString()));
    if (!config.notificationUrl().isBlank()) builder.notificationUrl(config.notificationUrl());
    if (!config.backUrlSuccess().isBlank()) {
      builder.backUrls(PreferenceBackUrlsRequest.builder().success(config.backUrlSuccess())
          .failure(blankToNull(config.backUrlFailure())).pending(blankToNull(config.backUrlPending())).build());
      if (config.backUrlSuccess().startsWith("https://")) builder.autoReturn("approved");
    }
    Preference preference = call("crear la preferencia", () -> preferences.create(builder.build(), options(null)));
    String url = config.sandboxCheckout() && preference.getSandboxInitPoint() != null ? preference.getSandboxInitPoint() : preference.getInitPoint();
    if (url == null || url.isBlank()) throw new DependencyUnavailableException("Mercado Pago no devolvió la URL de checkout");
    return url;
  }

  @Override
  public Optional<ProviderPayment> consultarPago(String paymentId) {
    requireConfigured();
    Long id = numericId(paymentId);
    if (id == null) return Optional.empty();
    try {
      return Optional.of(translate(call("consultar el pago", () -> payments.get(id, options(null)))));
    } catch (NotFoundAtProvider e) {
      return Optional.empty();
    }
  }

  @Override
  public List<ProviderPayment> buscarPagosPorReferencia(UUID transaccionId) {
    requireConfigured();
    MPSearchRequest search = MPSearchRequest.builder().limit(20).offset(0)
        .filters(Map.<String, Object>of("external_reference", transaccionId.toString(), "sort", "date_created", "criteria", "asc")).build();
    var page = call("buscar pagos", () -> payments.search(search, options(null)));
    if (page == null || page.getResults() == null) return List.of();
    return page.getResults().stream().map(this::translate).toList();
  }

  @Override
  public RefundResult reembolsar(String paymentId, BigDecimal monto, String idempotencyKey) {
    requireConfigured();
    Long id = numericId(paymentId);
    if (id == null) return new RefundResult(null, ResultadoProveedor.RECHAZADO, "Referencia de pago inválida: " + paymentId);
    try {
      PaymentRefund refund = call("reembolsar el pago", () -> refunds.refund(id, monto, options(idempotencyKey)));
      return new RefundResult(refund.getId() == null ? null : refund.getId().toString(), refundResult(refund.getStatus()), refund.getStatus());
    } catch (RejectedByProvider | NotFoundAtProvider e) {
      return new RefundResult(null, ResultadoProveedor.RECHAZADO, e.getMessage());
    }
  }

  static ResultadoProveedor paymentResult(String status) {
    if (status == null) return ResultadoProveedor.EN_PROCESO;
    return switch (status) {
      case "approved" -> ResultadoProveedor.APROBADO;
      case "rejected" -> ResultadoProveedor.RECHAZADO;
      case "cancelled" -> ResultadoProveedor.CANCELADO;
      // pending, in_process, authorized, in_mediation, refunded, charged_back: no cambian el cobro.
      default -> ResultadoProveedor.EN_PROCESO;
    };
  }

  static ResultadoProveedor refundResult(String status) {
    if (status == null) return ResultadoProveedor.EN_PROCESO;
    return switch (status) {
      case "approved" -> ResultadoProveedor.APROBADO;
      case "rejected", "cancelled" -> ResultadoProveedor.RECHAZADO;
      default -> ResultadoProveedor.EN_PROCESO;
    };
  }

  private ProviderPayment translate(Payment p) {
    BigDecimal fee = null;
    if (p.getFeeDetails() != null && !p.getFeeDetails().isEmpty()) {
      fee = p.getFeeDetails().stream().map(PaymentFeeDetail::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    return new ProviderPayment(p.getId() == null ? null : p.getId().toString(), p.getExternalReference(), paymentResult(p.getStatus()),
        p.getStatusDetail(), p.getTransactionAmount(), p.getCurrencyId(), fee);
  }

  private <T> T call(String operation, CheckedCall<T> action) {
    Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
      try {
        return action.run();
      } catch (MPApiException e) {
        int status = e.getStatusCode();
        if (status == 404) throw new NotFoundAtProvider("Mercado Pago no encontró el recurso al " + operation);
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new RejectedByProvider("Mercado Pago rechazó " + operation + " (HTTP " + status + ")");
        }
        throw new DependencyUnavailableException("Mercado Pago no disponible al " + operation + " (HTTP " + status + ")", e);
      } catch (Exception e) {
        throw new DependencyUnavailableException("Mercado Pago no respondió al " + operation, e);
      }
    });
    try {
      return guarded.get();
    } catch (CallNotPermittedException e) {
      throw new DependencyUnavailableException("Circuito abierto: Mercado Pago no disponible temporalmente", e);
    } catch (RejectedByProvider e) {
      if (operation.startsWith("reembolsar")) throw e;
      throw new DependencyUnavailableException(e.getMessage(), e);
    }
  }

  private MPRequestOptions options(String idempotencyKey) {
    MPRequestOptions.MPRequestOptionsBuilder builder = MPRequestOptions.builder()
        .accessToken(config.accessToken())
        .connectionTimeout(config.connectTimeoutMs())
        .connectionRequestTimeout(config.connectTimeoutMs())
        .socketTimeout(config.readTimeoutMs())
        .maxRetries(0);
    if (idempotencyKey != null) builder.customHeaders(Map.of("x-idempotency-key", idempotencyKey));
    return builder.build();
  }

  private void requireConfigured() {
    if (config.accessToken() == null || config.accessToken().isBlank()) {
      throw new DependencyUnavailableException("Integración con Mercado Pago no configurada");
    }
  }

  private static Long numericId(String value) {
    try { return value == null ? null : Long.valueOf(value.trim()); } catch (NumberFormatException e) { return null; }
  }

  private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

  @FunctionalInterface private interface CheckedCall<T> { T run() throws Exception; }
  private static final class NotFoundAtProvider extends RuntimeException { NotFoundAtProvider(String m) { super(m); } }
  private static final class RejectedByProvider extends RuntimeException { RejectedByProvider(String m) { super(m); } }
}
