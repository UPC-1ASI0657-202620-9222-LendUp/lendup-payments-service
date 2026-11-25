package com.lendup.pagos.config;
import com.lendup.pagos.domain.model.PoliticaDeComision; import java.math.BigDecimal; import org.springframework.beans.factory.annotation.Value;
import java.time.Clock; import org.springframework.context.annotation.*; import org.springframework.transaction.PlatformTransactionManager; import org.springframework.transaction.TransactionDefinition; import org.springframework.transaction.support.TransactionOperations; import org.springframework.transaction.support.TransactionTemplate;
@Configuration public class CoreConfiguration {
  @Bean Clock utcClock(){return Clock.systemUTC();}
  @Bean PoliticaDeComision politicaDeComision(@Value("${lendup.commission.percent}") BigDecimal porcentaje){return PoliticaDeComision.porcentaje(porcentaje);}
  /** Transacciones programáticas independientes: se usan antes y después de llamar a Mercado Pago, nunca durante la llamada. */
  @Bean TransactionOperations requiresNewTransaction(PlatformTransactionManager manager){TransactionTemplate t=new TransactionTemplate(manager);t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return t;}
}
