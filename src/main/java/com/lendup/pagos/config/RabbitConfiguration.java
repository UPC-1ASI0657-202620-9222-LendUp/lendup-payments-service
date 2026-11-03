package com.lendup.pagos.config;
import java.util.stream.Stream; import org.springframework.amqp.core.Binding; import org.springframework.amqp.core.BindingBuilder; import org.springframework.amqp.core.Declarable; import org.springframework.amqp.core.Declarables; import org.springframework.amqp.core.DirectExchange; import org.springframework.amqp.core.ExchangeBuilder; import org.springframework.amqp.core.Queue; import org.springframework.amqp.core.QueueBuilder; import org.springframework.amqp.core.TopicExchange; import org.springframework.context.annotation.*;
@Configuration public class RabbitConfiguration {
  public static final String EVENTS_EXCHANGE="lendup.domain.events"; public static final String DLX="lendup.domain.events.dlx"; public static final String QUEUE="payments.events.v1"; public static final String DLQ="payments.events.v1.dlq";
  public static final String RESERVATION_CONFIRMED="reservas.confirmada.v1"; public static final String RESERVATION_CANCELLED="reservas.cancelada.v1";
  @Bean TopicExchange domainExchange(){return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();} @Bean DirectExchange deadLetterExchange(){return ExchangeBuilder.directExchange(DLX).durable(true).build();}
  @Bean Queue paymentsEventsQueue(){return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();} @Bean Queue paymentsEventsDlq(){return QueueBuilder.durable(DLQ).build();}
  @Bean Binding dlqBinding(Queue paymentsEventsDlq,DirectExchange deadLetterExchange){return BindingBuilder.bind(paymentsEventsDlq).to(deadLetterExchange).with(DLQ);}
  @Bean Declarables consumedEventBindings(Queue paymentsEventsQueue,TopicExchange domainExchange,EventRoutingProperties routes){
    return new Declarables(Stream.of(RESERVATION_CONFIRMED,RESERVATION_CANCELLED,routes.loanCreatedRoutingKey(),routes.loanActivatedRoutingKey(),routes.loanFinishedRoutingKey(),routes.extensionAcceptedRoutingKey(),routes.incidentResolvedRoutingKey())
        .distinct().map(key->(Declarable)BindingBuilder.bind(paymentsEventsQueue).to(domainExchange).with(key)).toList());
  }
}
