package com.lendup.pagos.infrastructure.persistence.entity;

import com.lendup.pagos.domain.model.EstadoPrestamoPagable;
import com.lendup.pagos.domain.model.PeriodoPrestamo;
import com.lendup.pagos.domain.model.PrestamoPagable;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="prestamos_pagables")
public class PrestamoPagableEntity {
  @Id @Column(name="reserva_id") private UUID reservaId;
  @Column(name="prestamo_id") private UUID prestamoId;
  @Column(name="prestamista_usuario_id",nullable=false) private UUID prestamistaUsuarioId;
  @Column(name="prestatario_usuario_id",nullable=false) private UUID prestatarioUsuarioId;
  @Column(nullable=false) private LocalDateTime desde;
  @Column(nullable=false) private LocalDateTime hasta;
  @Column(name="tarifa_diaria_acordada",nullable=false) private BigDecimal tarifaDiariaAcordada;
  @Column(name="garantia_monetaria_acordada") private BigDecimal garantiaMonetariaAcordada;
  @Column(nullable=false,length=3) private String moneda;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private EstadoPrestamoPagable estado;
  @Column(name="costo_extension_pendiente") private BigDecimal costoExtensionPendiente;
  @Column(name="cambio_fecha_pendiente_id") private UUID cambioFechaPendienteId;
  @Column(name="creado_en",nullable=false) private Instant creadoEn;
  @Column(name="actualizado_en",nullable=false) private Instant actualizadoEn;
  protected PrestamoPagableEntity(){}

  public static PrestamoPagableEntity from(PrestamoPagable p){PrestamoPagableEntity e=new PrestamoPagableEntity();e.reservaId=p.reservaId();e.copy(p);return e;}
  public void copy(PrestamoPagable p){prestamoId=p.prestamoId();prestamistaUsuarioId=p.prestamistaUsuarioId();prestatarioUsuarioId=p.prestatarioUsuarioId();desde=p.periodo().desde();hasta=p.periodo().hasta();tarifaDiariaAcordada=p.tarifaDiariaAcordada();garantiaMonetariaAcordada=p.garantiaMonetariaAcordada();moneda=p.moneda();estado=p.estado();costoExtensionPendiente=p.costoExtensionPendiente();cambioFechaPendienteId=p.cambioFechaPendienteId();creadoEn=p.creadoEn();actualizadoEn=p.actualizadoEn();}
  public PrestamoPagable toDomain(){return new PrestamoPagable(reservaId,prestamoId,prestamistaUsuarioId,prestatarioUsuarioId,new PeriodoPrestamo(desde,hasta),tarifaDiariaAcordada,garantiaMonetariaAcordada,moneda,estado,costoExtensionPendiente,cambioFechaPendienteId,creadoEn,actualizadoEn);}
  public UUID getReservaId(){return reservaId;}
}
