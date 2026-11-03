package com.lendup.pagos.interfaces.rest.dto;
import com.lendup.pagos.application.port.in.PaymentViews.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
/** Respuestas en snake_case con los campos que consume services/api/mappers/domain.ts. */
public final class ResponseDtos { private ResponseDtos(){}
  public record MedioPagoResponse(String codigo,String nombre,String estado){public static MedioPagoResponse from(MedioPagoView m){return new MedioPagoResponse(m.codigo(),m.nombre(),m.estado());}}
  public record GarantiaResponse(UUID id,UUID prestamo_id,BigDecimal monto_acordado,String moneda,String estado,BigDecimal monto_constituido,BigDecimal monto_afectado,BigDecimal monto_devuelto,Instant constituida_en,Instant cerrada_en,Instant creada_en,Instant actualizada_en){
    public static GarantiaResponse from(GarantiaView g){return g==null?null:new GarantiaResponse(g.id(),g.prestamoId(),g.montoAcordado(),g.moneda(),g.estado(),g.montoConstituido(),g.montoAfectado(),g.montoDevuelto(),g.constituidaEn(),g.cerradaEn(),g.creadaEn(),g.actualizadaEn());}}
  public record TransaccionResponse(UUID id,UUID prestamo_id,UUID garantia_id,UUID cambio_fecha_prestamo_id,UUID incidencia_id,String tipo,String estado,BigDecimal monto,String moneda,BigDecimal comision_proveedor,String medio_pago_seleccionado,String referencia_proveedor,String clave_idempotencia,String checkout_url,Instant solicitada_en,Instant confirmada_en,Instant actualizada_en,GarantiaResponse garantia){
    public static TransaccionResponse from(TransaccionView t){return new TransaccionResponse(t.id(),t.prestamoId(),t.garantiaId(),t.cambioFechaPrestamoId(),t.incidenciaId(),t.tipo(),t.estado(),t.monto(),t.moneda(),t.comisionProveedor(),t.medioPagoSeleccionado(),t.referenciaProveedor(),t.claveIdempotencia(),t.checkoutUrl(),t.solicitadaEn(),t.confirmadaEn(),t.actualizadaEn(),GarantiaResponse.from(t.garantia()));}}
  public record WebhookResponse(String proveedor_evento_id,String estado){}
}
