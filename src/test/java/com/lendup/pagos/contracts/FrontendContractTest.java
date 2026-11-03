package com.lendup.pagos.contracts;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lendup.pagos.application.exception.BusinessConflictException;
import com.lendup.pagos.application.port.in.GuaranteeUseCases;
import com.lendup.pagos.application.port.in.PaymentUseCases;
import com.lendup.pagos.application.port.in.PaymentViews.*;
import com.lendup.pagos.config.security.ActorContext;
import com.lendup.pagos.interfaces.rest.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Contrato con el frontend (services/payments-api.service.ts y mappers/domain.ts): mismas rutas, cuerpos en
 * snake_case, códigos HTTP y formato de error {status, error, message} del monolito, más checkout_url.
 */
class FrontendContractTest {
  private final PaymentUseCases payments = mock(PaymentUseCases.class);
  private final GuaranteeUseCases guarantees = mock(GuaranteeUseCases.class);
  private final ActorContext actor = mock(ActorContext.class);
  private final UUID user = UUID.randomUUID(), prestamo = UUID.randomUUID();
  private MockMvc mvc;

  @BeforeEach void setup() {
    when(actor.userId()).thenReturn(user);
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc = MockMvcBuilders.standaloneSetup(new PaymentController(payments, actor), new GuaranteeController(guarantees, actor), new TransactionQueryController(payments, actor))
        .setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build();
  }

  private TransaccionView view(String tipo, GarantiaView garantia) {
    Instant now = Instant.parse("2026-11-01T15:00:00Z");
    return new TransaccionView(UUID.randomUUID(), prestamo, garantia == null ? null : garantia.id(), null, null, tipo, "PENDIENTE", new BigDecimal("60.00"), "PEN",
        null, "MERCADO_PAGO", null, "pago-1", "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=1", now, null, now, garantia);
  }

  @Test void mediosDePagoConCodigoNombreYEstado() throws Exception {
    when(payments.listarMediosPago()).thenReturn(List.of(new MedioPagoView("MERCADO_PAGO", "Mercado Pago", "ACTIVO")));
    mvc.perform(get("/api/v1/medios-pago")).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].codigo").value("MERCADO_PAGO")).andExpect(jsonPath("$[0].nombre").value("Mercado Pago")).andExpect(jsonPath("$[0].estado").value("ACTIVO"));
  }

  @Test void pagoAceptaElCuerpoDelFrontendYRespondeTransaccionConCheckoutUrl() throws Exception {
    when(payments.crearPago(eq(user), any(), any())).thenReturn(view("PAGO_TARIFA", null));
    String body = "{\"prestamo_id\":\"" + prestamo + "\",\"tipo\":\"PAGO_TARIFA\",\"monto\":60.00,\"medio_pago_seleccionado\":\"MERCADO_PAGO\",\"clave_idempotencia\":\"pago-1\"}";
    mvc.perform(post("/api/v1/pagos").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists()).andExpect(jsonPath("$.prestamo_id").value(prestamo.toString()))
        .andExpect(jsonPath("$.tipo").value("PAGO_TARIFA")).andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.monto").value(60.00)).andExpect(jsonPath("$.medio_pago_seleccionado").value("MERCADO_PAGO"))
        .andExpect(jsonPath("$.solicitada_en").value("2026-11-01T15:00:00Z")).andExpect(jsonPath("$.actualizada_en").exists())
        .andExpect(jsonPath("$.checkout_url", org.hamcrest.Matchers.startsWith("https://")));
    verify(payments).crearPago(eq(user), argThat(c -> c.tipo().equals("PAGO_TARIFA") && c.claveIdempotencia().equals("pago-1") && c.monto().compareTo(new BigDecimal("60.00")) == 0), any());
  }

  @Test void pagoInvalidoDevuelve400ConFormatoDeError() throws Exception {
    mvc.perform(post("/api/v1/pagos").contentType(MediaType.APPLICATION_JSON).content("{\"prestamo_id\":\"" + prestamo + "\",\"tipo\":\"OTRO\",\"monto\":1,\"medio_pago_seleccionado\":\"MERCADO_PAGO\",\"clave_idempotencia\":\"k\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.error").value("SOLICITUD_INVALIDA")).andExpect(jsonPath("$.message").exists());
    mvc.perform(post("/api/v1/pagos").contentType(MediaType.APPLICATION_JSON).content("{\"prestamo_id\":\"no-uuid\"}")).andExpect(status().isBadRequest());
  }

  @Test void garantiaAceptaCuerpoMinimoYRespondeGarantiaPendiente() throws Exception {
    Instant now = Instant.now();
    GarantiaView garantia = new GarantiaView(UUID.randomUUID(), prestamo, new BigDecimal("50.00"), "PEN", "PENDIENTE", null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, now, now);
    when(guarantees.constituirGarantia(eq(user), any(), any())).thenReturn(view("CONSTITUCION_GARANTIA", garantia));
    mvc.perform(post("/api/v1/garantias").contentType(MediaType.APPLICATION_JSON).content("{\"prestamo_id\":\"" + prestamo + "\"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("CONSTITUCION_GARANTIA"))
        .andExpect(jsonPath("$.garantia_id").value(garantia.id().toString())).andExpect(jsonPath("$.garantia.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.garantia.monto_acordado").value(50.00)).andExpect(jsonPath("$.checkout_url").exists());
  }

  @Test void garantiaRepetidaEs409ConFormatoDelMonolito() throws Exception {
    when(guarantees.constituirGarantia(any(), any(), any())).thenThrow(new BusinessConflictException("Garantía ya registrada"));
    mvc.perform(post("/api/v1/garantias").contentType(MediaType.APPLICATION_JSON).content("{\"prestamo_id\":\"" + prestamo + "\",\"medio_pago_seleccionado\":\"MERCADO_PAGO\",\"clave_idempotencia\":\"g\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.message").value("Garantía ya registrada"));
  }

  @Test void transaccionesExponenLosCamposDelMapper() throws Exception {
    when(payments.listarTransacciones(user, prestamo)).thenReturn(List.of(view("PAGO_TARIFA", null)));
    mvc.perform(get("/api/v1/prestamos/{id}/transacciones", prestamo)).andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").exists()).andExpect(jsonPath("$[0].prestamo_id").exists()).andExpect(jsonPath("$[0].tipo").exists())
        .andExpect(jsonPath("$[0].estado").exists()).andExpect(jsonPath("$[0].monto").exists()).andExpect(jsonPath("$[0].moneda").value("PEN"))
        .andExpect(jsonPath("$[0].medio_pago_seleccionado").exists()).andExpect(jsonPath("$[0].solicitada_en").exists()).andExpect(jsonPath("$[0].actualizada_en").exists());
  }
}
