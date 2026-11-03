package com.lendup.pagos.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.port.out.PaymentGatewayPort.CheckoutRequest;
import com.lendup.pagos.config.IntegrationProperties;
import com.lendup.pagos.domain.model.ResultadoProveedor;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;

/** Mercado Pago simulado con WireMock: preferencia, consulta de pago, búsqueda, reembolso, errores y timeout. */
class MercadoPagoAdapterWireMockTest {
  static WireMockServer server;
  MercadoPagoAdapter adapter;

  @BeforeAll static void start() { server = new WireMockServer(WireMockConfiguration.options().dynamicPort()); server.start(); }
  @AfterAll static void stop() { server.stop(); }

  @BeforeEach void setup() {
    server.resetAll();
    adapter = new MercadoPagoAdapter(new IntegrationProperties(null, new IntegrationProperties.MercadoPago(server.baseUrl(), "TEST-access-token", "secret",
        "https://pagos.lendup.test/api/v1/webhooks/mercado-pago", "https://app.lendup.test/payments/return?result=success", "", "", false, 500, 800)));
  }

  @Test void creaPreferenciaConExternalReferenceYDevuelveInitPoint() {
    UUID tx = UUID.randomUUID();
    server.stubFor(post(urlPathEqualTo("/checkout/preferences")).willReturn(okJson("{\"id\":\"pref-1\",\"init_point\":\"https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1\"}")));
    String url = adapter.crearCheckout(new CheckoutRequest(tx, UUID.randomUUID(), "LendUp - Tarifa del préstamo", new BigDecimal("60.00"), "PEN"));
    assertEquals("https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1", url);
    server.verify(postRequestedFor(urlPathEqualTo("/checkout/preferences"))
        .withHeader("Authorization", equalTo("Bearer TEST-access-token"))
        .withRequestBody(matchingJsonPath("$.external_reference", equalTo(tx.toString())))
        .withRequestBody(matchingJsonPath("$.notification_url", equalTo("https://pagos.lendup.test/api/v1/webhooks/mercado-pago")))
        .withRequestBody(matchingJsonPath("$.auto_return", equalTo("approved")))
        .withRequestBody(matchingJsonPath("$.items[0].currency_id", equalTo("PEN"))));
  }

  @Test void consultaPagoYTraduceEstados() {
    server.stubFor(get(urlPathEqualTo("/v1/payments/123")).willReturn(okJson("{\"id\":123,\"status\":\"approved\",\"status_detail\":\"accredited\",\"external_reference\":\"ref\",\"transaction_amount\":60.00,\"currency_id\":\"PEN\",\"fee_details\":[{\"type\":\"mercadopago_fee\",\"amount\":2.39,\"fee_payer\":\"collector\"}]}")));
    var payment = adapter.consultarPago("123").orElseThrow();
    assertEquals(ResultadoProveedor.APROBADO, payment.resultado());
    assertEquals("ref", payment.externalReference());
    assertEquals(0, new BigDecimal("2.39").compareTo(payment.comision()));
    assertEquals(ResultadoProveedor.RECHAZADO, MercadoPagoAdapter.paymentResult("rejected"));
    assertEquals(ResultadoProveedor.CANCELADO, MercadoPagoAdapter.paymentResult("cancelled"));
    assertEquals(ResultadoProveedor.EN_PROCESO, MercadoPagoAdapter.paymentResult("in_process"));
  }

  @Test void pagoInexistenteOIdNoNumericoNoEsError() {
    server.stubFor(get(urlPathEqualTo("/v1/payments/404")).willReturn(aResponse().withStatus(404).withBody("{\"message\":\"not found\"}")));
    assertTrue(adapter.consultarPago("404").isEmpty());
    assertTrue(adapter.consultarPago("pago-123").isEmpty());
  }

  @Test void buscaPagosPorExternalReference() {
    UUID tx = UUID.randomUUID();
    server.stubFor(get(urlPathEqualTo("/v1/payments/search")).withQueryParam("external_reference", equalTo(tx.toString()))
        .willReturn(okJson("{\"paging\":{\"total\":1,\"limit\":20,\"offset\":0},\"results\":[{\"id\":9,\"status\":\"rejected\",\"external_reference\":\"" + tx + "\",\"transaction_amount\":60,\"currency_id\":\"PEN\"}]}")));
    var results = adapter.buscarPagosPorReferencia(tx);
    assertEquals(1, results.size());
    assertEquals(ResultadoProveedor.RECHAZADO, results.getFirst().resultado());
  }

  @Test void reembolsoEnviaClaveDeIdempotenciaYMonto() {
    server.stubFor(post(urlPathEqualTo("/v1/payments/123/refunds")).willReturn(okJson("{\"id\":77,\"payment_id\":123,\"amount\":30.00,\"status\":\"approved\"}")));
    var result = adapter.reembolsar("123", new BigDecimal("30.00"), "devolucion-garantia:abc:finalizacion");
    assertEquals(ResultadoProveedor.APROBADO, result.resultado());
    assertEquals("77", result.refundId());
    server.verify(postRequestedFor(urlPathEqualTo("/v1/payments/123/refunds"))
        .withHeader("X-Idempotency-Key", equalTo("devolucion-garantia:abc:finalizacion"))
        .withRequestBody(matchingJsonPath("$[?(@.amount == 30)]")));
  }

  @Test void reembolsoRechazadoPorElProveedorEsDefinitivo() {
    server.stubFor(post(urlPathEqualTo("/v1/payments/123/refunds")).willReturn(aResponse().withStatus(400).withBody("{\"message\":\"invalid\"}")));
    assertEquals(ResultadoProveedor.RECHAZADO, adapter.reembolsar("123", BigDecimal.TEN, "k").resultado());
  }

  @Test void error5xxYTimeoutNoAsumenResultado() {
    server.stubFor(get(urlPathEqualTo("/v1/payments/500")).willReturn(aResponse().withStatus(500)));
    server.stubFor(post(urlPathEqualTo("/v1/payments/1/refunds")).willReturn(okJson("{\"id\":1,\"status\":\"approved\"}").withFixedDelay(2000)));
    assertThrows(DependencyUnavailableException.class, () -> adapter.consultarPago("500"));
    assertThrows(DependencyUnavailableException.class, () -> adapter.reembolsar("1", BigDecimal.TEN, "k"));
    server.verify(1, postRequestedFor(urlPathEqualTo("/v1/payments/1/refunds")));
  }

  @Test void sinAccessTokenFallaSeguro() {
    var unconfigured = new MercadoPagoAdapter(new IntegrationProperties(null, null));
    assertThrows(DependencyUnavailableException.class, () -> unconfigured.consultarPago("1"));
  }
}
