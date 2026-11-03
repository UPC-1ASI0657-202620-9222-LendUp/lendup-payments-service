package com.lendup.pagos.infrastructure.persistence.entity;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="notificaciones_proveedor")
public class NotificacionProveedorEntity {
  @Id private UUID id; @Column(name="proveedor_evento_id",nullable=false) private String proveedorEventoId; @Column(nullable=false) private String tipo;
  @Column(name="payload_json",nullable=false,columnDefinition="JSON") private String payloadJson; @Column(nullable=false) private String estado;
  @Column(name="recibido_en",nullable=false) private Instant recibidoEn; @Column(name="procesado_en") private Instant procesadoEn;
  protected NotificacionProveedorEntity(){}
  public NotificacionProveedorEntity(UUID id,String proveedorEventoId,String tipo,String payloadJson,Instant recibidoEn){this.id=id;this.proveedorEventoId=proveedorEventoId;this.tipo=tipo;this.payloadJson=payloadJson;this.estado="RECIBIDA";this.recibidoEn=recibidoEn;}
  public void marcar(String nuevoEstado,Instant now){estado=nuevoEstado;procesadoEn=now;}
  public UUID getId(){return id;} public String getProveedorEventoId(){return proveedorEventoId;} public String getTipo(){return tipo;} public String getEstado(){return estado;} public String getPayloadJson(){return payloadJson;} public Instant getProcesadoEn(){return procesadoEn;}
}
