package com.lendup.pagos.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lendup.pagos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.pagos.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

class OutboxRelayTest {
  @Test void conservaPendienteElEventoSiElBrokerEstaCaido(){
    OutboxJpaRepository repository=mock(OutboxJpaRepository.class); RabbitTemplate rabbit=mock(RabbitTemplate.class);
    OutboxEventEntity event=new OutboxEventEntity(UUID.randomUUID(),"TRANSACCION",UUID.randomUUID(),"PagoConfirmado",1,1,"{}","corr",Instant.now());
    when(repository.pending(any(Pageable.class))).thenReturn(java.util.List.of(event));
    doThrow(new AmqpConnectException(new java.net.ConnectException("broker caido"))).when(rabbit).convertAndSend(anyString(),anyString(),any(byte[].class),any(MessagePostProcessor.class),any(CorrelationData.class));
    new OutboxRelay(repository,rabbit,JsonMapper.builder().findAndAddModules().build(),Clock.systemUTC()).relay();
    assertNull(event.getPublishedAt()); assertEquals(1,event.getAttempts()); assertNotNull(event.getLastError()); verify(repository).save(event);
  }
}
