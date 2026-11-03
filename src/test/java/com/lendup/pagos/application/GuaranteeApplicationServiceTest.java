package com.lendup.pagos.application;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.pagos.application.exception.BusinessConflictException;
import com.lendup.pagos.application.port.in.PaymentViews.*;
import com.lendup.pagos.domain.model.*;
import com.lendup.pagos.support.PaymentsTestFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuaranteeApplicationServiceTest {
  private final PaymentsTestFixture f = new PaymentsTestFixture();
  private final BigDecimal monto = new BigDecimal("50.00");

  private UUID garantiaConstituida(UUID prestamo) {
    var view = f.guarantees.constituirGarantia(f.prestatario, new ConstituirGarantiaCommand(prestamo, "MERCADO_PAGO", "g1"), "corr");
    f.payments.aplicarResultadoCobro(f.gateway.registrarPago(view.id(), ResultadoProveedor.APROBADO, monto), "corr");
    return view.garantia().id();
  }

  private void ejecutar(List<UUID> reembolsos) { reembolsos.forEach(id -> f.payments.ejecutarReembolso(id, "corr")); }

  @Test void constituirCreaGarantiaPendienteConCheckout() {
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    var view = f.guarantees.constituirGarantia(f.prestatario, new ConstituirGarantiaCommand(prestamo, null, null), "corr");
    assertEquals("CONSTITUCION_GARANTIA", view.tipo());
    assertEquals("PENDIENTE", view.estado());
    assertEquals("PENDIENTE", view.garantia().estado());
    assertEquals(monto, view.monto());
    assertNotNull(view.checkoutUrl());
  }

  @Test void conflictoSiNoRequiereGarantiaOSiYaEstaConstituida() {
    UUID sinGarantia = f.prestamoReservado(new BigDecimal("20"), null);
    assertThrows(BusinessConflictException.class, () -> f.guarantees.constituirGarantia(f.prestatario, new ConstituirGarantiaCommand(sinGarantia, null, "x"), "corr"));
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    garantiaConstituida(prestamo);
    assertThrows(BusinessConflictException.class, () -> f.guarantees.constituirGarantia(f.prestatario, new ConstituirGarantiaCommand(prestamo, null, "g2"), "corr"));
  }

  @Test void webhookAprobadoConstituyeYPublicaGarantiaConstituida() {
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    UUID garantia = garantiaConstituida(prestamo);
    assertEquals(EstadoGarantia.CONSTITUIDA, f.repository.garantias.get(garantia).estado());
    assertEquals(List.of("GarantiaConstituida"), f.eventTypes());
  }

  @Test void prestamoFinalizadoDevuelveTodaLaGarantia() {
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    UUID garantia = garantiaConstituida(prestamo);
    var reembolsos = f.guarantees.registrarPrestamoFinalizado(prestamo, "corr");
    assertEquals(1, reembolsos.size());
    ejecutar(reembolsos);
    var g = f.repository.garantias.get(garantia);
    assertEquals(EstadoGarantia.DEVUELTA, g.estado());
    assertEquals(monto, g.montoDevuelto());
    assertEquals("GarantiaDevuelta", f.eventTypes().getLast());
    assertTrue(f.guarantees.registrarPrestamoFinalizado(prestamo, "corr").isEmpty());
  }

  @Test void incidenciaParcialRetieneYDevuelveElSaldo() {
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    UUID garantia = garantiaConstituida(prestamo);
    UUID incidencia = UUID.randomUUID();
    var reembolsos = f.guarantees.registrarIncidenciaResuelta(new IncidenciaResueltaCommand(incidencia, prestamo, DecisionGarantia.AFECTACION_PARCIAL, new BigDecimal("20")), "corr");
    ejecutar(reembolsos);
    var g = f.repository.garantias.get(garantia);
    assertEquals(EstadoGarantia.AFECTADA_PARCIALMENTE, g.estado());
    assertEquals(new BigDecimal("20.00"), g.montoAfectado());
    assertEquals(new BigDecimal("30.00"), g.montoDevuelto());
    var retencion = f.repository.ofType(TipoTransaccion.RETENCION_GARANTIA).getFirst();
    assertEquals(incidencia, retencion.incidenciaId());
    assertTrue(f.eventTypes().containsAll(List.of("GarantiaRetenida", "GarantiaDevuelta")));
    assertTrue(f.guarantees.registrarPrestamoFinalizado(prestamo, "corr").isEmpty(), "No queda saldo por devolver");
  }

  @Test void incidenciaTotalNoDevuelveNada() {
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    UUID garantia = garantiaConstituida(prestamo);
    var reembolsos = f.guarantees.registrarIncidenciaResuelta(new IncidenciaResueltaCommand(UUID.randomUUID(), prestamo, DecisionGarantia.AFECTACION_TOTAL, monto), "corr");
    assertTrue(reembolsos.isEmpty());
    assertEquals(EstadoGarantia.AFECTADA_TOTALMENTE, f.repository.garantias.get(garantia).estado());
    assertFalse(f.eventTypes().contains("GarantiaDevuelta"));
  }

  @Test void cancelacionDevuelveGarantiaConstituidaYCancelaLaPendiente() {
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    UUID garantia = garantiaConstituida(prestamo);
    ejecutar(f.payments.registrarReservaCancelada(f.reservaDe(prestamo), "corr"));
    assertEquals(EstadoGarantia.DEVUELTA, f.repository.garantias.get(garantia).estado());

    UUID otro = f.prestamoReservado(new BigDecimal("20"), monto);
    var pendiente = f.guarantees.constituirGarantia(f.prestatario, new ConstituirGarantiaCommand(otro, null, "p1"), "corr");
    assertTrue(f.payments.registrarReservaCancelada(f.reservaDe(otro), "corr").isEmpty());
    assertEquals(EstadoGarantia.CANCELADA, f.repository.garantias.get(pendiente.garantia().id()).estado());
    assertEquals(EstadoTransaccion.CANCELADA, f.repository.transacciones.get(pendiente.id()).estado());
  }

  @Test void reembolsoRechazadoQuedaFallidoSinTocarLaGarantia() {
    UUID prestamo = f.prestamoReservado(new BigDecimal("20"), monto);
    UUID garantia = garantiaConstituida(prestamo);
    f.gateway.refundResult = ResultadoProveedor.RECHAZADO;
    var reembolsos = f.guarantees.registrarPrestamoFinalizado(prestamo, "corr");
    ejecutar(reembolsos);
    assertEquals(EstadoTransaccion.FALLIDA, f.repository.transacciones.get(reembolsos.getFirst()).estado());
    assertEquals(EstadoGarantia.CONSTITUIDA, f.repository.garantias.get(garantia).estado());
  }
}
