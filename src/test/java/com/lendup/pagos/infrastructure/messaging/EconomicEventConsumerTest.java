package com.lendup.pagos.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lendup.pagos.application.port.in.GuaranteeUseCases;
import com.lendup.pagos.application.port.in.PaymentUseCases;
import com.lendup.pagos.config.EventRoutingProperties;
import com.lendup.pagos.domain.model.DecisionGarantia;
import com.lendup.pagos.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Mensajes de ejemplo de contracts/events/v1/examples.json, incluidos los de productores aún pendientes. */
class EconomicEventConsumerTest {
  private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  private final ProcessedEventJpaRepository processed = mock(ProcessedEventJpaRepository.class);
  private final PaymentUseCases payments = mock(PaymentUseCases.class);
  private final GuaranteeUseCases guarantees = mock(GuaranteeUseCases.class);
  private final EconomicEventConsumer consumer = new EconomicEventConsumer(json, processed, payments, guarantees, EventRoutingProperties.defaults(), Clock.systemUTC());

  private byte[] event(UUID id, String type, Map<String, Object> payload) {
    return json.writeValueAsBytes(Map.of("event_id", id, "event_type", type, "event_version", 1, "aggregate_version", 1, "aggregate_id", UUID.randomUUID(),
        "occurred_at", "2026-11-01T15:00:00Z", "correlation_id", "corr", "payload", payload));
  }

  @Test void reservaConfirmadaCreaLaProyeccion() {
    UUID reserva = UUID.randomUUID();
    Map<String, Object> payload = new HashMap<>(Map.of("reserva_id", reserva, "solicitud_id", UUID.randomUUID(), "publicacion_id", UUID.randomUUID(),
        "prestamista_usuario_id", UUID.randomUUID(), "prestatario_usuario_id", UUID.randomUUID(), "desde", "2026-11-10T09:00:00", "hasta", "2026-11-12T09:00:00",
        "tarifa_diaria_acordada", 20.00, "moneda_acordada", "PEN"));
    payload.put("garantia_monetaria_acordada", null);
    consumer.handle("reservas.confirmada.v1", event(UUID.randomUUID(), "ReservaConfirmada", payload));
    verify(payments).registrarReservaConfirmada(argThat(c -> c.reservaId().equals(reserva) && c.garantiaMonetariaAcordada() == null
        && c.tarifaDiariaAcordada().compareTo(new BigDecimal("20")) == 0 && c.moneda().equals("PEN")), eq("corr"));
    verify(processed).save(any());
  }

  @Test void eventoRepetidoSeDescartaPorInbox() {
    UUID id = UUID.randomUUID();
    when(processed.existsById(id)).thenReturn(true);
    consumer.handle("reservas.cancelada.v1", event(id, "ReservaCancelada", Map.of("reserva_id", UUID.randomUUID())));
    verifyNoInteractions(payments);
    verify(processed, never()).save(any());
  }

  @Test void eventosPendientesUsanRoutingKeysConfigurables() {
    UUID prestamo = UUID.randomUUID(), reserva = UUID.randomUUID(), cambio = UUID.randomUUID(), incidencia = UUID.randomUUID();
    consumer.handle("prestamos.creado.v1", event(UUID.randomUUID(), "PrestamoCreado", Map.of("prestamo_id", prestamo, "reserva_id", reserva)));
    consumer.handle("prestamos.activado.v1", event(UUID.randomUUID(), "PrestamoActivado", Map.of("prestamo_id", prestamo)));
    consumer.handle("prestamos.extension-aceptada.v1", event(UUID.randomUUID(), "ExtensionAceptada", Map.of("prestamo_id", prestamo, "cambio_fecha_prestamo_id", cambio, "costo_adicional", 20.00, "moneda", "PEN")));
    consumer.handle("prestamos.finalizado.v1", event(UUID.randomUUID(), "PrestamoFinalizado", Map.of("prestamo_id", prestamo)));
    consumer.handle("evidencias.incidencia-resuelta.v1", event(UUID.randomUUID(), "IncidenciaResuelta", Map.of("incidencia_id", incidencia, "prestamo_id", prestamo,
        "decision_garantia", "AFECTACION_PARCIAL", "monto_garantia_afectado", 20.00, "moneda", "PEN")));
    verify(payments).vincularPrestamo(reserva, prestamo, "corr");
    verify(payments).registrarPrestamoActivado(prestamo, "corr");
    verify(payments).registrarExtensionAceptada(eq(prestamo), eq(cambio), argThat(c -> c.compareTo(new BigDecimal("20")) == 0), eq("corr"));
    verify(guarantees).registrarPrestamoFinalizado(prestamo, "corr");
    verify(guarantees).registrarIncidenciaResuelta(argThat(c -> c.decision() == DecisionGarantia.AFECTACION_PARCIAL && c.incidenciaId().equals(incidencia)), eq("corr"));
  }

  @Test void reembolsosCreadosSeEjecutanTrasProcesar() {
    UUID reembolso = UUID.randomUUID();
    when(payments.registrarReservaCancelada(any(), any())).thenReturn(List.of(reembolso));
    consumer.handle("reservas.cancelada.v1", event(UUID.randomUUID(), "ReservaCancelada", Map.of("reserva_id", UUID.randomUUID(), "solicitud_id", UUID.randomUUID())));
    verify(payments).ejecutarReembolso(reembolso, "corr");
  }

  @Test void payloadInvalidoORoutingKeyDesconocidaFallanParaIrALaDlq() {
    assertThrows(IllegalArgumentException.class, () -> consumer.handle("reservas.cancelada.v1", event(UUID.randomUUID(), "ReservaCancelada", Map.of())));
    assertThrows(IllegalArgumentException.class, () -> consumer.handle("otro.evento.v1", event(UUID.randomUUID(), "Otro", Map.of("x", 1))));
    verify(processed, never()).save(any());
  }
}
