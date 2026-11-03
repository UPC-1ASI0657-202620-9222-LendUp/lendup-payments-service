package com.lendup.pagos.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="lendup.integrations")
public record IntegrationProperties(Identity identity, MercadoPago mercadoPago){
  public IntegrationProperties {
    if(identity==null)identity=new Identity("","","",2000,3000);
    if(mercadoPago==null)mercadoPago=new MercadoPago("https://api.mercadopago.com","","","","","","",false,2000,5000);
  }
  public record Identity(String baseUrl,String serviceToken,String firebaseUserUriTemplate,int connectTimeoutMs,int readTimeoutMs){}
  public record MercadoPago(String apiBaseUrl,String accessToken,String webhookSecret,String notificationUrl,String backUrlSuccess,String backUrlFailure,String backUrlPending,boolean sandboxCheckout,int connectTimeoutMs,int readTimeoutMs){}
}
