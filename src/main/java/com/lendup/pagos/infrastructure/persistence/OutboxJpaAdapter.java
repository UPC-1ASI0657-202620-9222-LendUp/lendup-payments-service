package com.lendup.pagos.infrastructure.persistence;
import com.lendup.pagos.application.port.out.OutboxPort; import com.lendup.pagos.domain.event.EventEnvelope; import com.lendup.pagos.infrastructure.persistence.entity.OutboxEventEntity; import com.lendup.pagos.infrastructure.persistence.repository.OutboxJpaRepository; import org.springframework.stereotype.Component; import tools.jackson.databind.json.JsonMapper;
@Component public class OutboxJpaAdapter implements OutboxPort { private final OutboxJpaRepository repo; private final JsonMapper json; public OutboxJpaAdapter(OutboxJpaRepository r,JsonMapper j){repo=r;json=j;}
  public void append(EventEnvelope e){try{repo.save(new OutboxEventEntity(e.eventId(),aggregateType(e.eventType()),e.aggregateId(),e.eventType(),e.eventVersion(),e.aggregateVersion(),json.writeValueAsString(e.payload()),e.correlationId(),e.occurredAt()));}catch(Exception ex){throw new IllegalStateException("No se pudo serializar el evento",ex);}}
  private String aggregateType(String eventType){return eventType.startsWith("Garantia")?"GARANTIA":"TRANSACCION";}
}
