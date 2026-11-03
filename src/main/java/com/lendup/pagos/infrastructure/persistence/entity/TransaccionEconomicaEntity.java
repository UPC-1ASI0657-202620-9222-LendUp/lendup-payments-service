package com.lendup.pagos.infrastructure.persistence.entity;

import com.lendup.pagos.domain.model.EstadoTransaccion;
import com.lendup.pagos.domain.model.TipoTransaccion;
import com.lendup.pagos.domain.model.TransaccionEconomica;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="transacciones_economicas")
public class TransaccionEconomicaEntity {
  @Id private UUID id;
  @Column(name="prestamo_id",nullable=false) private UUID prestamoId;
  @Column(name="garantia_id") private UUID garantiaId;
  @Column(name="cambio_fecha_prestamo_id") private UUID cambioFechaPrestamoId;
  @Column(name="incidencia_id") private UUID incidenciaId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private TipoTransaccion tipo;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private EstadoTransaccion estado;
  @Column(nullable=false) private BigDecimal monto;
  @Column(nullable=false,length=3) private String moneda;
  @Column(name="comision_proveedor") private BigDecimal comisionProveedor;
  @Column(name="medio_pago_seleccionado") private String medioPagoSeleccionado;
  @Column(name="referencia_proveedor") private String referenciaProveedor;
  @Column(name="clave_idempotencia",nullable=false) private String claveIdempotencia;
  @Column(name="checkout_url") private String checkoutUrl;
  @Column(name="solicitada_en",nullable=false) private Instant solicitadaEn;
  @Column(name="confirmada_en") private Instant confirmadaEn;
  @Column(name="actualizada_en",nullable=false) private Instant actualizadaEn;
  protected TransaccionEconomicaEntity(){}

  public static TransaccionEconomicaEntity from(TransaccionEconomica t){TransaccionEconomicaEntity e=new TransaccionEconomicaEntity();e.id=t.id();e.copy(t);return e;}
  public void copy(TransaccionEconomica t){prestamoId=t.prestamoId();garantiaId=t.garantiaId();cambioFechaPrestamoId=t.cambioFechaPrestamoId();incidenciaId=t.incidenciaId();tipo=t.tipo();estado=t.estado();monto=t.monto();moneda=t.moneda();comisionProveedor=t.comisionProveedor();medioPagoSeleccionado=t.medioPagoSeleccionado();referenciaProveedor=t.referenciaProveedor();claveIdempotencia=t.claveIdempotencia();checkoutUrl=t.checkoutUrl();solicitadaEn=t.solicitadaEn();confirmadaEn=t.confirmadaEn();actualizadaEn=t.actualizadaEn();}
  public TransaccionEconomica toDomain(){return new TransaccionEconomica(id,prestamoId,garantiaId,cambioFechaPrestamoId,incidenciaId,tipo,estado,monto,moneda,comisionProveedor,medioPagoSeleccionado,referenciaProveedor,claveIdempotencia,checkoutUrl,solicitadaEn,confirmadaEn,actualizadaEn);}
  public UUID getId(){return id;}
}
