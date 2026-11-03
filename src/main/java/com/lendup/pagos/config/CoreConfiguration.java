package com.lendup.pagos.config;
import java.time.Clock; import org.springframework.context.annotation.*; import org.springframework.transaction.PlatformTransactionManager; import org.springframework.transaction.TransactionDefinition; import org.springframework.transaction.support.TransactionOperations; import org.springframework.transaction.support.TransactionTemplate;
@Configuration public class CoreConfiguration {
  @Bean Clock utcClock(){return Clock.systemUTC();}
  /** Transacciones programáticas independientes: se usan antes y después de llamar a Mercado Pago, nunca durante la llamada. */
  @Bean TransactionOperations requiresNewTransaction(PlatformTransactionManager manager){TransactionTemplate t=new TransactionTemplate(manager);t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return t;}
}
