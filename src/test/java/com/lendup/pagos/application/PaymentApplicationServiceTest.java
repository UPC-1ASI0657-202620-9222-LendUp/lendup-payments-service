package com.lendup.pagos.application;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.pagos.application.exception.*;
import com.lendup.pagos.application.port.in.PaymentViews.CrearPagoCommand;
import com.lendup.pagos.domain.model.*;
import com.lendup.pagos.support.PaymentsTestFixture;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentApplicationServiceTest {
  private final PaymentsTestFixture f = new PaymentsTestFixture();
  private final BigDecimal tarifa = new BigDecimal("20.00");
  private final BigDecimal total = new BigDecimal("60.00"); // 48 h + 1 s = 3 bloques iniciados

  private CrearPagoCommand pago(UUID prestamo, BigDecimal monto, String clave) {
    return new CrearPagoCommand(prestamo, null, "PAGO_TARIFA", monto, "MERCADO_PAGO", clave);
  }

  @Test void mediosDePagoDevuelveMercadoPagoActivo() {
    var methods = f.payments.listarMediosPago();
    assertEquals(1, methods.size());
    assertEquals("MERCADO_PAGO", methods.getFirst().codigo());
    assertEquals("ACTIVO", methods.getFirst().estado());
  }

  @Test void crearPagoRegistraPendienteConCheckoutYAuditoria() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    assertEquals("PENDIENTE", view.estado());
    assertEquals("PAGO_TARIFA", view.tipo());
    assertEquals(total, view.monto());
    assertTrue(view.checkoutUrl().startsWith("https://"));
    assertTrue(f.auditActions.contains("TRANSACCION:SOLICITAR_PAGO_TARIFA:PENDIENTE"));
    assertTrue(f.events.isEmpty(), "No se publica nada hasta que Mercado Pago confirme");
  }

  @Test void mismaClaveDevuelveLaMismaTransaccion() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var first = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    var second = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    assertEquals(first.id(), second.id());
    assertEquals(1, f.repository.transacciones.size());
  }

  @Test void otraClaveConPagoEnCursoReutilizaElPendiente() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var first = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    var second = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k2"), "corr");
    assertEquals(first.id(), second.id());
  }

  @Test void claveUsadaEnOtroPrestamoEsConflicto() {
    UUID a = f.prestamoReservado(tarifa, null), b = f.prestamoReservado(tarifa, null);
    f.payments.crearPago(f.prestatario, pago(a, total, "k1"), "corr");
    assertThrows(BusinessConflictException.class, () -> f.payments.crearPago(f.prestatario, pago(b, total, "k1"), "corr"));
  }

  @Test void validaMontoParticipanteYMedio() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    assertThrows(IllegalArgumentException.class, () -> f.payments.crearPago(f.prestatario, pago(prestamo, new BigDecimal("40.00"), "k1"), "corr"));
    assertThrows(ForbiddenException.class, () -> f.payments.crearPago(f.prestamista, pago(prestamo, total, "k2"), "corr"));
    assertThrows(ForbiddenException.class, () -> f.payments.crearPago(UUID.randomUUID(), pago(prestamo, total, "k3"), "corr"));
    assertThrows(IllegalArgumentException.class, () -> f.payments.crearPago(f.prestatario,
        new CrearPagoCommand(prestamo, null, "PAGO_TARIFA", total, "PAGO_EN_LINEA", "k4"), "corr"));
    assertThrows(NotFoundException.class, () -> f.payments.crearPago(f.prestatario, pago(UUID.randomUUID(), total, "k5"), "corr"));
  }

  @Test void webhookAprobadoConfirmaYPublicaPagoConfirmadoUnaSolaVez() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    var mp = f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, total);
    f.payments.aplicarResultadoCobro(mp, "corr");
    f.payments.aplicarResultadoCobro(mp, "corr");
    var t = f.repository.transacciones.get(view.id());
    assertEquals(EstadoTransaccion.CONFIRMADA, t.estado());
    assertEquals(mp.paymentId(), t.referenciaProveedor());
    assertEquals(1, f.eventTypes().stream().filter("PagoConfirmado"::equals).count());
    assertThrows(BusinessConflictException.class, () -> f.payments.crearPago(f.prestatario, pago(prestamo, total, "k2"), "corr"));
  }

  @Test void montoDistintoAlEsperadoNoConfirma() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    f.payments.aplicarResultadoCobro(f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, new BigDecimal("1.00")), "corr");
    assertEquals(EstadoTransaccion.PENDIENTE, f.repository.transacciones.get(view.id()).estado());
    assertTrue(f.events.isEmpty());
  }

  @Test void rechazoPublicaPagoRechazadoYPermiteReintentar() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    f.payments.aplicarResultadoCobro(f.gateway.registrarPago(view.id(), ResultadoProveedor.RECHAZADO, total), "corr");
    assertEquals(EstadoTransaccion.RECHAZADA, f.repository.transacciones.get(view.id()).estado());
    assertEquals("PagoRechazado", f.eventTypes().getLast());
    var retry = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k2"), "corr");
    assertNotEquals(view.id(), retry.id());
  }

  @Test void proveedorCaidoAlCrearCheckoutDevuelve503YLaClaveReintenta() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    f.gateway.unavailable = true;
    assertThrows(DependencyUnavailableException.class, () -> f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr"));
    f.gateway.unavailable = false;
    var retry = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    assertNotNull(retry.checkoutUrl());
    assertEquals(1, f.repository.transacciones.size());
  }

  @Test void prestamoActivadoLiberaLaTarifaUnaSolaVez() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    f.payments.aplicarResultadoCobro(f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, total), "corr");
    f.payments.registrarPrestamoActivado(prestamo, "corr");
    f.payments.registrarPrestamoActivado(prestamo, "corr");
    var liberaciones = f.repository.ofType(TipoTransaccion.LIBERACION_TARIFA);
    assertEquals(1, liberaciones.size());
    assertEquals(EstadoTransaccion.CONFIRMADA, liberaciones.getFirst().estado());
    assertEquals(1, f.eventTypes().stream().filter("TarifaLiberada"::equals).count());
  }

  @Test void extensionAceptadaSePagaConSuCostoYSeLiberaAlConfirmar() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    f.payments.registrarPrestamoActivado(prestamo, "corr");
    UUID cambio = UUID.randomUUID();
    f.payments.registrarExtensionAceptada(prestamo, cambio, new BigDecimal("20"), "corr");
    assertThrows(IllegalArgumentException.class, () -> f.payments.crearPago(f.prestatario,
        new CrearPagoCommand(prestamo, UUID.randomUUID(), "PAGO_EXTENSION", new BigDecimal("20.00"), "MERCADO_PAGO", "e0"), "corr"));
    var view = f.payments.crearPago(f.prestatario, new CrearPagoCommand(prestamo, cambio, "PAGO_EXTENSION", new BigDecimal("20.00"), "MERCADO_PAGO", "e1"), "corr");
    f.payments.aplicarResultadoCobro(f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, new BigDecimal("20.00")), "corr");
    assertNull(f.repository.findProyeccionByPrestamo(prestamo).orElseThrow().costoExtensionPendiente());
    assertTrue(f.eventTypes().containsAll(java.util.List.of("PagoConfirmado", "TarifaLiberada")));
  }

  @Test void reservaCanceladaReembolsaLaTarifaConClaveIdempotente() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    var mp = f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, total);
    f.payments.aplicarResultadoCobro(mp, "corr");
    var reembolsos = f.payments.registrarReservaCancelada(f.reservaDe(prestamo), "corr");
    assertEquals(1, reembolsos.size());
    assertTrue(f.payments.registrarReservaCancelada(f.reservaDe(prestamo), "corr").isEmpty(), "El mismo evento no genera otro reembolso");
    f.payments.ejecutarReembolso(reembolsos.getFirst(), "corr");
    f.payments.ejecutarReembolso(reembolsos.getFirst(), "corr");
    var reembolso = f.repository.transacciones.get(reembolsos.getFirst());
    assertEquals(EstadoTransaccion.CONFIRMADA, reembolso.estado());
    assertEquals(1, f.gateway.refundKeys.size());
    assertEquals("reembolso:" + view.id(), f.gateway.refundKeys.getFirst());
    assertEquals("ReembolsoProcesado", f.eventTypes().getLast());
  }

  @Test void timeoutEnReembolsoConservaPendienteYLaConciliacionReintenta() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    f.payments.aplicarResultadoCobro(f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, total), "corr");
    UUID reembolso = f.payments.registrarReservaCancelada(f.reservaDe(prestamo), "corr").getFirst();
    f.gateway.unavailable = true;
    f.payments.ejecutarReembolso(reembolso, "corr");
    assertEquals(EstadoTransaccion.PENDIENTE, f.repository.transacciones.get(reembolso).estado());
    f.gateway.unavailable = false;
    f.payments.conciliarPendientes(Instant.now().plusSeconds(1), 50);
    assertEquals(EstadoTransaccion.CONFIRMADA, f.repository.transacciones.get(reembolso).estado());
  }

  @Test void pagoQueLlegaDespuesDeCancelarSeReembolsa() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    f.payments.registrarReservaCancelada(f.reservaDe(prestamo), "corr");
    assertEquals(EstadoTransaccion.CANCELADA, f.repository.transacciones.get(view.id()).estado());
    var reembolsos = f.payments.aplicarResultadoCobro(f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, total), "corr");
    assertEquals(1, reembolsos.size());
    assertFalse(f.eventTypes().contains("PagoConfirmado"));
  }

  @Test void conciliacionConfirmaPendientesSinWebhook() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    var view = f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, total);
    f.payments.conciliarPendientes(Instant.now().plusSeconds(1), 50);
    assertEquals(EstadoTransaccion.CONFIRMADA, f.repository.transacciones.get(view.id()).estado());
  }

  @Test void soloParticipantesConsultanTransacciones() {
    UUID prestamo = f.prestamoReservado(tarifa, null);
    f.payments.crearPago(f.prestatario, pago(prestamo, total, "k1"), "corr");
    assertEquals(1, f.payments.listarTransacciones(f.prestamista, prestamo).size());
    assertThrows(ForbiddenException.class, () -> f.payments.listarTransacciones(UUID.randomUUID(), prestamo));
  }
}
