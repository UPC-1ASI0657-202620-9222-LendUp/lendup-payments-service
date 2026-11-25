package com.lendup.pagos.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** Routing keys de Préstamos (disponibles) y de Evidencias (contrato pendiente: aún no hay microservicio). */
@ConfigurationProperties(prefix="lendup.events")
public record EventRoutingProperties(String loanCreatedRoutingKey,String loanActivatedRoutingKey,String loanReturnedRoutingKey,String loanFinishedRoutingKey,String extensionAcceptedRoutingKey,String incidentReportedRoutingKey,String incidentResolvedRoutingKey){
  public EventRoutingProperties {
    if(loanCreatedRoutingKey==null||loanCreatedRoutingKey.isBlank())loanCreatedRoutingKey="prestamos.creado.v1";
    if(loanActivatedRoutingKey==null||loanActivatedRoutingKey.isBlank())loanActivatedRoutingKey="prestamos.activado.v1";
    if(loanReturnedRoutingKey==null||loanReturnedRoutingKey.isBlank())loanReturnedRoutingKey="prestamos.devolucion-registrada.v1";
    if(loanFinishedRoutingKey==null||loanFinishedRoutingKey.isBlank())loanFinishedRoutingKey="prestamos.finalizado.v1";
    if(extensionAcceptedRoutingKey==null||extensionAcceptedRoutingKey.isBlank())extensionAcceptedRoutingKey="prestamos.extension-aceptada.v1";
    if(incidentReportedRoutingKey==null||incidentReportedRoutingKey.isBlank())incidentReportedRoutingKey="evidencias.incidencia-reportada.v1";
    if(incidentResolvedRoutingKey==null||incidentResolvedRoutingKey.isBlank())incidentResolvedRoutingKey="evidencias.incidencia-resuelta.v1";
  }
  public static EventRoutingProperties defaults(){return new EventRoutingProperties(null,null,null,null,null,null,null);}
}
