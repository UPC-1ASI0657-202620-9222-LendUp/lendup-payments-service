package com.lendup.pagos.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lendup.pagos.application.port.in.PaymentViews.ReservaConfirmadaCommand;
import com.lendup.pagos.application.service.PaymentApplicationService;
import com.lendup.pagos.infrastructure.persistence.repository.*;
import com.lendup.pagos.support.TestAuthConfiguration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** MERCADOPAGO_MODE=simulado: el equipo prueba pago, webhook y eventos sin credenciales de Mercado Pago. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
    "lendup.outbox.relay-delay-ms=3600000", "lendup.reconciliation.delay-ms=3600000",
    "lendup.integrations.mercado-pago.mode=simulado", "lendup.integrations.mercado-pago.simulator-base-url=http://localhost:8082",
    "lendup.integrations.mercado-pago.back-url-success=http://localhost:3000/payments/return?result=success",
    "lendup.integrations.mercado-pago.back-url-failure=http://localhost:3000/payments/return?result=failure"})
class SimulatedMercadoPagoIT {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4").withDatabaseName("lendup_pagos").withUsername("test").withPassword("test");

  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired WebApplicationContext context; @Autowired PaymentApplicationService payments; @Autowired JsonMapper json;
  @Autowired TransaccionEconomicaJpaRepository transacciones; @Autowired GarantiaJpaRepository garantias; @Autowired PrestamoPagableJpaRepository proyecciones;
  @Autowired NotificacionProveedorJpaRepository notificaciones; @Autowired OutboxJpaRepository outbox;
  MockMvc mvc; UUID prestamo;

  @BeforeEach void setup() {
    outbox.deleteAll(); notificaciones.deleteAll(); transacciones.deleteAll(); garantias.deleteAll(); proyecciones.deleteAll();
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    UUID reserva = UUID.randomUUID(); prestamo = UUID.randomUUID();
    LocalDateTime desde = LocalDateTime.of(2026, 11, 10, 9, 0);
    payments.registrarReservaConfirmada(new ReservaConfirmadaCommand(reserva, TestAuthConfiguration.usuario("prestamista"), TestAuthConfiguration.usuario("prestatario"),
        desde, desde.plusHours(48), new BigDecimal("20.00"), new BigDecimal("50.00"), "PEN"), "it");
    payments.vincularPrestamo(reserva, prestamo, "it");
  }

  @Test void garantiaSePagaEnElSimuladorYDejaElEventoEnElOutbox() throws Exception {
    String body = mvc.perform(post("/api/v1/garantias").header("Authorization", "Bearer prestatario").contentType(MediaType.APPLICATION_JSON)
            .content("{\"prestamo_id\":\"" + prestamo + "\",\"medio_pago_seleccionado\":\"MERCADO_PAGO\",\"clave_idempotencia\":\"g-sim-1\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    var created = json.readTree(body);
    String checkoutUrl = created.get("checkout_url").asString();
    assertTrue(checkoutUrl.startsWith("http://localhost:8082/api/v1/simulador/mercado-pago/checkout/"), checkoutUrl);
    String transaccionId = checkoutUrl.substring(checkoutUrl.lastIndexOf('/') + 1);

    mvc.perform(get("/api/v1/simulador/mercado-pago/checkout/{id}", transaccionId)).andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Aprobar pago")));
    mvc.perform(post("/api/v1/simulador/mercado-pago/checkout/{id}", transaccionId).param("resultado", "aprobado").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("APROBADO"))
        .andExpect(jsonPath("$.estado_webhook").value("PROCESADA")).andExpect(jsonPath("$.estado_transaccion").value("CONFIRMADA"));

    assertTrue(outbox.findAll().stream().anyMatch(e -> e.getEventType().equals("GarantiaConstituida")));
    assertEquals(1, notificaciones.count());
    mvc.perform(get("/api/v1/prestamos/{id}/transacciones", prestamo).header("Authorization", "Bearer prestatario"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].estado").value("CONFIRMADA"));
  }

  @Test void desdeElNavegadorRedirigeALaUrlDeRetornoDelFrontend() throws Exception {
    String body = mvc.perform(post("/api/v1/pagos").header("Authorization", "Bearer prestatario").contentType(MediaType.APPLICATION_JSON)
            .content("{\"prestamo_id\":\"" + prestamo + "\",\"tipo\":\"PAGO_TARIFA\",\"monto\":40.00,\"medio_pago_seleccionado\":\"MERCADO_PAGO\",\"clave_idempotencia\":\"p-sim-1\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = json.readTree(body).get("id").asString();
    mvc.perform(post("/api/v1/simulador/mercado-pago/checkout/{id}", id).param("resultado", "rechazado").accept(MediaType.TEXT_HTML))
        .andExpect(status().isSeeOther()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("http://localhost:3000/payments/return?result=failure&payment_id=")));
    assertTrue(outbox.findAll().stream().anyMatch(e -> e.getEventType().equals("PagoRechazado")));
  }
}
