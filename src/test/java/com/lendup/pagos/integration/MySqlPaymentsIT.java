package com.lendup.pagos.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.lendup.pagos.application.port.in.PaymentViews.ReservaConfirmadaCommand;
import com.lendup.pagos.application.service.PaymentApplicationService;
import com.lendup.pagos.domain.model.*;
import com.lendup.pagos.infrastructure.messaging.EconomicEventConsumer;
import com.lendup.pagos.infrastructure.persistence.repository.*;
import com.lendup.pagos.support.TestAuthConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** MySQL real (Flyway + validate), Mercado Pago simulado con WireMock y la API completa con seguridad. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
    "lendup.outbox.relay-delay-ms=3600000", "lendup.reconciliation.delay-ms=3600000"})
class MySqlPaymentsIT {
  static final String SECRET = "it-webhook-secret";
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4").withDatabaseName("lendup_pagos").withUsername("test").withPassword("test");
  static final WireMockServer mercadoPago = new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @BeforeAll static void startMercadoPago() { mercadoPago.start(); }
  @AfterAll static void stopMercadoPago() { mercadoPago.stop(); }

  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
    if (!mercadoPago.isRunning()) mercadoPago.start();
    r.add("lendup.integrations.mercado-pago.api-base-url", mercadoPago::baseUrl);
    r.add("lendup.integrations.mercado-pago.access-token", () -> "TEST-it-token");
    r.add("lendup.integrations.mercado-pago.webhook-secret", () -> SECRET);
  }

  @Autowired WebApplicationContext context; @Autowired PaymentApplicationService payments; @Autowired EconomicEventConsumer consumer; @Autowired JsonMapper json;
  @Autowired TransaccionEconomicaJpaRepository transacciones; @Autowired GarantiaJpaRepository garantias; @Autowired PrestamoPagableJpaRepository proyecciones;
  @Autowired NotificacionProveedorJpaRepository notificaciones; @Autowired OutboxJpaRepository outbox; @Autowired ProcessedEventJpaRepository processed;
  @Autowired AuditJpaRepository audits; @Autowired PlatformTransactionManager transactionManager;
  MockMvc mvc; UUID prestamo, reserva;

  @BeforeEach void setup() {
    mercadoPago.resetAll();
    processed.deleteAll(); outbox.deleteAll(); notificaciones.deleteAll(); transacciones.deleteAll(); garantias.deleteAll(); proyecciones.deleteAll();
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    mercadoPago.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/checkout/preferences"))
        .willReturn(okJson("{\"id\":\"pref-1\",\"init_point\":\"https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1\"}")));
    reserva = UUID.randomUUID(); prestamo = UUID.randomUUID();
    LocalDateTime desde = LocalDateTime.of(2026, 11, 10, 9, 0);
    payments.registrarReservaConfirmada(new ReservaConfirmadaCommand(reserva, TestAuthConfiguration.usuario("prestamista"), TestAuthConfiguration.usuario("prestatario"),
        desde, desde.plusHours(48).plusSeconds(1), new BigDecimal("20.00"), new BigDecimal("50.00"), "PEN"), "it");
    payments.vincularPrestamo(reserva, prestamo, "it");
  }

  private String pagoBody(String clave) {
    return "{\"prestamo_id\":\"" + prestamo + "\",\"tipo\":\"PAGO_TARIFA\",\"monto\":60.00,\"medio_pago_seleccionado\":\"MERCADO_PAGO\",\"clave_idempotencia\":\"" + clave + "\"}";
  }

  private String crearPago(String clave) throws Exception {
    String body = mvc.perform(post("/api/v1/pagos").header("Authorization", "Bearer prestatario").contentType(MediaType.APPLICATION_JSON).content(pagoBody(clave)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.checkout_url").exists())
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("id").asString();
  }

  private static String sign(String dataId, String requestId, String ts) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
    return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
  }

  private org.springframework.test.web.servlet.ResultActions webhook(String notificationId, String paymentId, String signature) throws Exception {
    return mvc.perform(post("/api/v1/webhooks/mercado-pago").queryParam("data.id", paymentId).queryParam("type", "payment")
        .header("x-signature", signature).header("x-request-id", "req-" + notificationId).contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":" + notificationId + ",\"type\":\"payment\",\"action\":\"payment.updated\",\"data\":{\"id\":\"" + paymentId + "\"}}"));
  }

  @Test void pagoIdempotentePorClaveConAuditoriaYSinEventosHastaConfirmar() throws Exception {
    String first = crearPago("pago-it-1");
    String second = crearPago("pago-it-1");
    assertEquals(first, second);
    assertEquals(1, transacciones.count());
    assertEquals(0, outbox.count());
    assertTrue(audits.count() >= 3);
  }

  @Test void webhookFirmadoConfirmaUnaVezYPublicaEnElOutbox() throws Exception {
    String tx = crearPago("pago-it-2");
    mercadoPago.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/v1/payments/555"))
        .willReturn(okJson("{\"id\":555,\"status\":\"approved\",\"external_reference\":\"" + tx + "\",\"transaction_amount\":60.00,\"currency_id\":\"PEN\"}")));
    String signature = sign("555", "req-9001", "1730473200");
    for (int i = 0; i < 3; i++) webhook("9001", "555", signature).andExpect(status().isCreated());
    assertEquals(EstadoTransaccion.CONFIRMADA, transacciones.findById(UUID.fromString(tx)).orElseThrow().toDomain().estado());
    assertEquals(1, outbox.count());
    assertEquals("PagoConfirmado", outbox.findAll().getFirst().getEventType());
    assertEquals("PROCESADA", notificaciones.findByProveedorEventoId("mp:9001").orElseThrow().getEstado());
    mercadoPago.verify(1, getRequestedFor(urlPathEqualTo("/v1/payments/555")));
  }

  @Test void webhookSinFirmaValidaEs401YNoTocaNada() throws Exception {
    webhook("9002", "556", "ts=1,v1=deadbeef").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("FIRMA_INVALIDA"));
    assertEquals(0, notificaciones.count());
  }

  @Test void cancelacionDeduplicaElEventoYReembolsaDespuesDelCommit() throws Exception {
    String tx = crearPago("pago-it-3");
    mercadoPago.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/v1/payments/777"))
        .willReturn(okJson("{\"id\":777,\"status\":\"approved\",\"external_reference\":\"" + tx + "\",\"transaction_amount\":60.00,\"currency_id\":\"PEN\"}")));
    mercadoPago.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/v1/payments/777/refunds")).willReturn(okJson("{\"id\":1,\"status\":\"approved\"}")));
    webhook("9003", "777", sign("777", "req-9003", "1730473200")).andExpect(status().isCreated());
    byte[] event = json.writeValueAsBytes(Map.of("event_id", UUID.randomUUID(), "event_type", "ReservaCancelada", "event_version", 1, "aggregate_version", 2,
        "aggregate_id", reserva, "occurred_at", Instant.now().toString(), "correlation_id", "it", "payload", Map.of("reserva_id", reserva)));
    consumer.handle("reservas.cancelada.v1", event);
    consumer.handle("reservas.cancelada.v1", event);
    assertEquals(1, processed.count());
    var reembolsos = transacciones.findAll().stream().map(e -> e.toDomain()).filter(t -> t.tipo() == TipoTransaccion.REEMBOLSO_TARIFA).toList();
    assertEquals(1, reembolsos.size());
    assertEquals(EstadoTransaccion.CONFIRMADA, reembolsos.getFirst().estado());
    mercadoPago.verify(1, postRequestedFor(urlPathEqualTo("/v1/payments/777/refunds")).withHeader("X-Idempotency-Key", equalTo("reembolso:" + tx)));
  }

  @Test void dosSolicitudesConcurrentesConLaMismaClaveCreanUnaTransaccion() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> call = () -> mvc.perform(post("/api/v1/pagos").header("Authorization", "Bearer prestatario").contentType(MediaType.APPLICATION_JSON)
          .content(pagoBody("pago-concurrente"))).andReturn().getResponse().getStatus();
      var a = pool.submit(call); var b = pool.submit(call);
      assertTrue(Set.of(201, 409).containsAll(Set.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))));
      assertEquals(1, transacciones.count());
    } finally { pool.shutdownNow(); }
  }

  @Test void negocioYOutboxSeReviertenJuntos() {
    var tx = new TransactionTemplate(transactionManager);
    var pago = TransaccionEconomica.nueva(prestamo, null, null, null, TipoTransaccion.PAGO_TARIFA, new BigDecimal("60.00"), "PEN", "MERCADO_PAGO", "999", "pago-it-4", Instant.now());
    pago.aplicarResultadoCobro(ResultadoProveedor.APROBADO, "999", null, Instant.now());
    transacciones.save(com.lendup.pagos.infrastructure.persistence.entity.TransaccionEconomicaEntity.from(pago));
    assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> { payments.registrarPrestamoActivado(prestamo, "it"); throw new RuntimeException("fallo forzado"); }));
    assertEquals(0, outbox.count());
    assertEquals(1, transacciones.count());
    assertEquals(EstadoPrestamoPagable.RESERVADO, proyecciones.findById(reserva).orElseThrow().toDomain().estado());
  }

  @Test void seguridadExigeTokenSalvoHealthYWebhook() throws Exception {
    mvc.perform(get("/api/v1/medios-pago")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/medios-pago").header("Authorization", "Bearer prestatario")).andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").value("MERCADO_PAGO"));
    mvc.perform(get("/api/v1/prestamos/{id}/transacciones", prestamo).header("Authorization", "Bearer intruso")).andExpect(status().isForbidden());
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }
}
