package com.lendup.pagos.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lendup.pagos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.pagos.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

class OutboxRelayTest {
  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private final OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final OutboxEventEntity event = new OutboxEventEntity(UUID.randomUUID(), "TRANSACCION", UUID.randomUUID(), "PagoConfirmado", 1, 1, "{}", "corr", NOW);

  private OutboxRelay relay() {
    when(repository.pending(any(Instant.class), anyInt(), any(Pageable.class))).thenReturn(List.of(event));
    return new OutboxRelay(repository, rabbit, JsonMapper.builder().findAndAddModules().build(), Clock.fixed(NOW, ZoneOffset.UTC), 10);
  }

  @Test void conservaPendienteElEventoSiElBrokerEstaCaido() {
    doThrow(new AmqpConnectException(new java.net.ConnectException("broker caido"))).when(rabbit)
        .convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
    relay().relay();
    assertNull(event.getPublishedAt()); assertEquals(1, event.getAttempts()); assertNotNull(event.getLastError());
    assertTrue(event.getNextAttemptAt().isAfter(NOW)); verify(repository).save(event);
  }

  @Test void conservaPendienteElEventoSiNingunaColaLoRecibe() {
    // Con mandatory=true el broker confirma (ACK) pero devuelve el mensaje si no hay cola enlazada a la routing key.
    doAnswer(inv -> {
      CorrelationData cd = inv.getArgument(4);
      cd.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "lendup.domain.events", "pagos.pago-confirmado.v1"));
      cd.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbit).convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
    relay().relay();
    assertNull(event.getPublishedAt());
    assertEquals(1, event.getAttempts());
    assertTrue(event.getLastError().contains("pagos.pago-confirmado.v1"));
  }

  @Test void marcaPublicadoCuandoElBrokerConfirmaYEnruta() {
    doAnswer(inv -> { ((CorrelationData) inv.getArgument(4)).getFuture().complete(new CorrelationData.Confirm(true, null)); return null; })
        .when(rabbit).convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
    relay().relay();
    assertEquals(NOW, event.getPublishedAt()); assertEquals(0, event.getAttempts());
  }

  @Test void tipoDesconocidoNoSePublicaConRoutingKeyInventada() {
    OutboxEventEntity unknown = new OutboxEventEntity(UUID.randomUUID(), "TRANSACCION", UUID.randomUUID(), "Desconocido", 1, 1, "{}", "corr", NOW);
    when(repository.pending(any(Instant.class), anyInt(), any(Pageable.class))).thenReturn(List.of(unknown));
    new OutboxRelay(repository, rabbit, JsonMapper.builder().findAndAddModules().build(), Clock.fixed(NOW, ZoneOffset.UTC), 10).relay();
    assertNull(unknown.getPublishedAt()); assertEquals(1, unknown.getAttempts());
    verifyNoInteractions(rabbit);
  }
}
