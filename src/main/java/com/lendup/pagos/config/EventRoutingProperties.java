package com.lendup.pagos.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** Routing keys de productores que todavía no publican sus eventos (contratos pendientes). */
@ConfigurationProperties(prefix="lendup.events")
public record EventRoutingProperties(String loanCreatedRoutingKey,String loanActivatedRoutingKey,String loanFinishedRoutingKey,String extensionAcceptedRoutingKey,String incidentResolvedRoutingKey){
  public EventRoutingProperties {
    if(loanCreatedRoutingKey==null||loanCreatedRoutingKey.isBlank())loanCreatedRoutingKey="prestamos.creado.v1";
    if(loanActivatedRoutingKey==null||loanActivatedRoutingKey.isBlank())loanActivatedRoutingKey="prestamos.activado.v1";
    if(loanFinishedRoutingKey==null||loanFinishedRoutingKey.isBlank())loanFinishedRoutingKey="prestamos.finalizado.v1";
    if(extensionAcceptedRoutingKey==null||extensionAcceptedRoutingKey.isBlank())extensionAcceptedRoutingKey="prestamos.extension-aceptada.v1";
    if(incidentResolvedRoutingKey==null||incidentResolvedRoutingKey.isBlank())incidentResolvedRoutingKey="evidencias.incidencia-resuelta.v1";
  }
  public static EventRoutingProperties defaults(){return new EventRoutingProperties(null,null,null,null,null);}
}
