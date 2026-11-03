package com.lendup.pagos.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.pagos.application.port.in.PaymentViews.*;
import com.lendup.pagos.domain.model.*;
import com.lendup.pagos.support.PaymentsTestFixture;
import io.cucumber.java.es.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Pasos de aceptación sobre los servicios de aplicación reales con persistencia en memoria y Mercado Pago falso. */
public class PaymentsSteps {
  private final PaymentsTestFixture f = new PaymentsTestFixture();
  private UUID prestamo;
  private UUID ultimaTransaccion;
  private RuntimeException error;
  private List<MedioPagoView> medios = List.of();
  private List<TransaccionView> consulta = List.of();

  @Dado("un préstamo reservado con tarifa diaria de {double} PEN por {int} bloques de 24 horas y sin garantía")
  public void prestamoSinGarantia(double tarifa, int bloques) { prestamo = f.prestamoReservado(money(tarifa), null, bloques); }

  @Dado("un préstamo reservado con tarifa diaria de {double} PEN por {int} bloques de 24 horas y garantía de {double} PEN")
  public void prestamoConGarantia(double tarifa, int bloques, double garantia) { prestamo = f.prestamoReservado(money(tarifa), money(garantia), bloques); }

  @Cuando("consulto los medios de pago")
  public void consultoMedios() { medios = f.payments.listarMediosPago(); }

  @Entonces("veo el medio {string} en estado {string}")
  public void veoMedio(String codigo, String estado) { assertTrue(medios.stream().anyMatch(m -> m.codigo().equals(codigo) && m.estado().equals(estado))); }

  @Cuando("el prestatario paga la tarifa de {double} PEN con la clave {string}")
  public void prestatarioPaga(double monto, String clave) { pagar(f.prestatario, monto, clave); }

  @Cuando("el prestamista paga la tarifa de {double} PEN con la clave {string}")
  public void prestamistaPaga(double monto, String clave) { pagar(f.prestamista, monto, clave); }

  @Cuando("el prestatario constituye la garantía")
  public void constituyeGarantia() {
    capture(() -> f.guarantees.constituirGarantia(f.prestatario, new ConstituirGarantiaCommand(prestamo, "MERCADO_PAGO", "garantia-" + UUID.randomUUID()), "bdd"))
        .ifPresent(v -> ultimaTransaccion = v.id());
  }

  @Cuando("Mercado Pago notifica {int} vez/veces que el pago fue {string}")
  public void mercadoPagoNotifica(int veces, String resultado) {
    TransaccionEconomica t = transaccion();
    var payment = f.gateway.registrarPago(t.id(), ResultadoProveedor.valueOf(resultado), t.monto());
    for (int i = 0; i < veces; i++) f.payments.aplicarResultadoCobro(payment, "bdd").forEach(id -> f.payments.ejecutarReembolso(id, "bdd"));
  }

  @Entonces("la última transacción queda en estado {string}")
  public void ultimaEnEstado(String estado) { assertNull(error, () -> "Error inesperado: " + error); assertEquals(estado, transaccion().estado().name()); }

  @Entonces("la transacción tiene el medio {string} y una URL de checkout de Mercado Pago")
  public void tieneMedioYCheckout(String medio) {
    assertEquals(medio, transaccion().medioPagoSeleccionado());
    assertTrue(transaccion().checkoutUrl().startsWith("https://www.mercadopago.com"));
  }

  @Entonces("se publica(n) {int} evento(s) {string}")
  public void sePublican(int cantidad, String tipo) { assertEquals(cantidad, f.eventTypes().stream().filter(tipo::equals).count(), () -> "Eventos: " + f.eventTypes()); }

  @Entonces("el prestatario puede volver a pagar la tarifa con la clave {string}")
  public void puedeReintentar(String clave) {
    UUID anterior = ultimaTransaccion;
    pagar(f.prestatario, transaccion().monto().doubleValue(), clave);
    assertNull(error);
    assertNotEquals(anterior, ultimaTransaccion);
    assertEquals(EstadoTransaccion.PENDIENTE, transaccion().estado());
  }

  @Entonces("la operación es rechazada con el mensaje {string}")
  public void rechazada(String mensaje) { assertNotNull(error, "Se esperaba un rechazo"); assertEquals(mensaje, error.getMessage()); }

  @Entonces("la garantía queda en estado {string} por {double} PEN")
  public void garantiaEnEstado(String estado, double monto) {
    Garantia g = f.repository.findGarantiaByPrestamo(prestamo).orElseThrow();
    assertEquals(estado, g.estado().name());
    assertEquals(money(monto), g.montoAcordado());
  }

  @Cuando("el prestamista consulta las transacciones del préstamo")
  public void prestamistaConsulta() { consultar(f.prestamista); }

  @Cuando("el prestatario consulta las transacciones del préstamo")
  public void prestatarioConsulta() { consultar(f.prestatario); }

  @Cuando("un estudiante ajeno consulta las transacciones del préstamo")
  public void ajenoConsulta() { consultar(UUID.randomUUID()); }

  @Entonces("ve {int} transacciones")
  public void veTransacciones(int cantidad) { assertEquals(cantidad, consulta.size()); }

  @Entonces("ve una transacción {string} en estado {string}")
  public void veTransaccion(String tipo, String estado) { assertTrue(consulta.stream().anyMatch(t -> t.tipo().equals(tipo) && t.estado().equals(estado)), () -> "Transacciones: " + consulta); }

  @Cuando("el préstamo finaliza")
  public void prestamoFinaliza() { ejecutar(f.guarantees.registrarPrestamoFinalizado(prestamo, "bdd")); }

  @Cuando("se resuelve una incidencia con decisión {string} por {double} PEN")
  public void incidenciaResuelta(String decision, double monto) {
    ejecutar(f.guarantees.registrarIncidenciaResuelta(new IncidenciaResueltaCommand(UUID.randomUUID(), prestamo, DecisionGarantia.valueOf(decision), money(monto)), "bdd"));
  }

  @Entonces("Mercado Pago reembolsa {double} PEN de la garantía")
  public void reembolsoGarantia(double monto) { assertEquals(money(monto), reembolsado(TipoTransaccion.CONSTITUCION_GARANTIA)); }

  @Entonces("Mercado Pago reembolsa {double} PEN de la tarifa")
  public void reembolsoTarifa(double monto) { assertEquals(money(monto), reembolsado(TipoTransaccion.PAGO_TARIFA)); }

  @Dado("que Mercado Pago no responde")
  public void mercadoPagoNoResponde() { f.gateway.unavailable = true; }

  @Dado("que Mercado Pago rechaza los reembolsos")
  public void mercadoPagoRechaza() { f.gateway.refundResult = ResultadoProveedor.RECHAZADO; }

  @Cuando("la reserva se cancela antes de la entrega")
  public void reservaCancelada() { ejecutar(f.payments.registrarReservaCancelada(f.reservaDe(prestamo), "bdd")); }

  @Cuando("Mercado Pago vuelve a responder y corre la conciliación")
  public void conciliacion() { f.gateway.unavailable = false; f.payments.conciliarPendientes(Instant.now().plusSeconds(1), 50); }

  @Entonces("hay {int} reembolsos en estado {string}")
  public void reembolsosEnEstado(int cantidad, String estado) {
    long count = f.repository.transacciones.values().stream().filter(t -> t.tipo().esReembolso() && t.estado().name().equals(estado)).count();
    assertEquals(cantidad, count);
  }

  private void pagar(UUID actor, double monto, String clave) {
    capture(() -> f.payments.crearPago(actor, new CrearPagoCommand(prestamo, null, "PAGO_TARIFA", money(monto), "MERCADO_PAGO", clave), "bdd"))
        .ifPresent(v -> ultimaTransaccion = v.id());
  }

  private void consultar(UUID actor) { capture(() -> f.payments.listarTransacciones(actor, prestamo)).ifPresent(v -> consulta = v); }

  /** Simula la ejecución después del commit que hace el consumidor de eventos. */
  private void ejecutar(List<UUID> reembolsos) { reembolsos.forEach(id -> f.payments.ejecutarReembolso(id, "bdd")); }

  private BigDecimal reembolsado(TipoTransaccion tipoOrigen) {
    return f.repository.ofType(tipoOrigen).stream().map(TransaccionEconomica::referenciaProveedor).filter(Objects::nonNull)
        .map(ref -> f.gateway.refundedAmounts.getOrDefault(ref, BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
  }

  private TransaccionEconomica transaccion() { return f.repository.findTransaccion(ultimaTransaccion).orElseThrow(); }

  private <T> Optional<T> capture(Supplier<T> action) {
    try { error = null; return Optional.of(action.get()); } catch (RuntimeException e) { error = e; return Optional.empty(); }
  }

  private static BigDecimal money(double value) { return BigDecimal.valueOf(value).setScale(2); }
}
