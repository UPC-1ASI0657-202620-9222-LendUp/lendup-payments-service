package com.lendup.pagos.infrastructure.persistence.entity;

import com.lendup.pagos.domain.model.EstadoGarantia;
import com.lendup.pagos.domain.model.Garantia;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="garantias")
public class GarantiaEntity {
  @Id private UUID id;
  @Column(name="prestamo_id",nullable=false) private UUID prestamoId;
  @Column(name="monto_acordado",nullable=false) private BigDecimal montoAcordado;
  @Column(nullable=false,length=3) private String moneda;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private EstadoGarantia estado;
  @Column(name="monto_constituido") private BigDecimal montoConstituido;
  @Column(name="monto_afectado",nullable=false) private BigDecimal montoAfectado;
  @Column(name="monto_devuelto",nullable=false) private BigDecimal montoDevuelto;
  @Column(name="constituida_en") private Instant constituidaEn;
  @Column(name="cerrada_en") private Instant cerradaEn;
  @Column(name="creada_en",nullable=false) private Instant creadaEn;
  @Column(name="actualizada_en",nullable=false) private Instant actualizadaEn;
  protected GarantiaEntity(){}

  public static GarantiaEntity from(Garantia g){GarantiaEntity e=new GarantiaEntity();e.id=g.id();e.copy(g);return e;}
  public void copy(Garantia g){prestamoId=g.prestamoId();montoAcordado=g.montoAcordado();moneda=g.moneda();estado=g.estado();montoConstituido=g.montoConstituido();montoAfectado=g.montoAfectado();montoDevuelto=g.montoDevuelto();constituidaEn=g.constituidaEn();cerradaEn=g.cerradaEn();creadaEn=g.creadaEn();actualizadaEn=g.actualizadaEn();}
  public Garantia toDomain(){return new Garantia(id,prestamoId,montoAcordado,moneda,estado,montoConstituido,montoAfectado,montoDevuelto,constituidaEn,cerradaEn,creadaEn,actualizadaEn);}
  public UUID getId(){return id;}
}
