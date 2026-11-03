package com.lendup.pagos.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.lendup.pagos.application.port.out.OutboxPort;
import com.lendup.pagos.config.RabbitConfiguration;
import com.lendup.pagos.domain.event.EventEnvelope;
import com.lendup.pagos.infrastructure.messaging.OutboxRelay;
import com.lendup.pagos.infrastructure.persistence.repository.*;
import com.lendup.pagos.support.TestAuthConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.json.JsonMapper;

/** RabbitMQ real: publicación con publisher confirms, consumo con inbox y DLQ ante mensajes inválidos. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"lendup.outbox.relay-delay-ms=3600000", "lendup.reconciliation.delay-ms=3600000"})
class RabbitMessagingIT {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4").withDatabaseName("lendup_pagos").withUsername("test").withPassword("test");
  @Container static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.2-management-alpine");

  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.rabbitmq.host", rabbit::getHost); r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    r.add("spring.rabbitmq.username", rabbit::getAdminUsername); r.add("spring.rabbitmq.password", rabbit::getAdminPassword);
  }

  @Autowired OutboxPort outbox; @Autowired OutboxRelay relay; @Autowired OutboxJpaRepository outboxRepository; @Autowired RabbitAdmin admin; @Autowired RabbitTemplate template;
  @Autowired PrestamoPagableJpaRepository proyecciones; @Autowired ProcessedEventJpaRepository processed; @Autowired JsonMapper json;

  @BeforeEach void clean() { outboxRepository.deleteAll(); processed.deleteAll(); proyecciones.deleteAll(); }

  @Test void publicaEventoYMarcaOutboxSoloTrasConfirmacion() {
    String queue = "capture-" + UUID.randomUUID();
    admin.declareQueue(new org.springframework.amqp.core.Queue(queue, false, true, true));
    admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, RabbitConfiguration.EVENTS_EXCHANGE, "pagos.pago-confirmado.v1", null));
    UUID eventId = UUID.randomUUID(), aggregate = UUID.randomUUID();
    outbox.append(new EventEnvelope(eventId, "PagoConfirmado", 1, 1, aggregate, Instant.now(), "corr", Map.of("transaccion_id", aggregate, "prestamo_id", UUID.randomUUID())));
    relay.relay();
    Message received = template.receive(queue, 5000);
    assertNotNull(received);
    assertEquals(eventId.toString(), received.getMessageProperties().getMessageId());
    assertTrue(new String(received.getBody()).contains("\"event_type\":\"PagoConfirmado\""));
    assertNotNull(outboxRepository.findById(eventId).orElseThrow().getPublishedAt());
  }

  @Test void consumeReservaConfirmadaDesdeElBrokerUnaSolaVez() {
    UUID reserva = UUID.randomUUID(), eventId = UUID.randomUUID();
    Map<String, Object> payload = new HashMap<>(Map.of("reserva_id", reserva, "solicitud_id", UUID.randomUUID(), "publicacion_id", UUID.randomUUID(),
        "prestamista_usuario_id", UUID.randomUUID(), "prestatario_usuario_id", UUID.randomUUID(), "desde", "2026-11-10T09:00:00", "hasta", "2026-11-12T09:00:00",
        "tarifa_diaria_acordada", 20.00, "moneda_acordada", "PEN", "lugar_intercambio_acordado", "Campus"));
    byte[] body = json.writeValueAsBytes(Map.of("event_id", eventId, "event_type", "ReservaConfirmada", "event_version", 1, "aggregate_version", 1,
        "aggregate_id", reserva, "occurred_at", "2026-11-01T15:00:00Z", "correlation_id", "it", "payload", payload));
    template.send(RabbitConfiguration.EVENTS_EXCHANGE, RabbitConfiguration.RESERVATION_CONFIRMED, new Message(body));
    template.send(RabbitConfiguration.EVENTS_EXCHANGE, RabbitConfiguration.RESERVATION_CONFIRMED, new Message(body));
    await().atMost(Duration.ofSeconds(15)).until(() -> proyecciones.findById(reserva).isPresent() && processed.existsById(eventId));
    assertEquals(1, processed.count());
  }

  @Test void mensajeInvalidoTerminaEnLaDlq() {
    byte[] body = "{\"event_id\":\"no-es-uuid\"}".getBytes();
    template.send(RabbitConfiguration.EVENTS_EXCHANGE, RabbitConfiguration.RESERVATION_CANCELLED, new Message(body));
    Message dead = await().atMost(Duration.ofSeconds(20)).until(() -> template.receive(RabbitConfiguration.DLQ, 500), Objects::nonNull);
    assertArrayEquals(body, dead.getBody());
  }
}
