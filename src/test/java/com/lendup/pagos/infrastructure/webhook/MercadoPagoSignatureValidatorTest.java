package com.lendup.pagos.infrastructure.webhook;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.exception.InvalidWebhookSignatureException;
import com.lendup.pagos.config.IntegrationProperties;
import org.junit.jupiter.api.Test;

class MercadoPagoSignatureValidatorTest {
  static final String SECRET = "test-webhook-secret";

  static IntegrationProperties props(String secret) {
    return new IntegrationProperties(null, new IntegrationProperties.MercadoPago("https://api.mercadopago.com", "TEST-token", secret, "", "", "", "", false, 2000, 5000));
  }

  /** Firma tal como la calcula Mercado Pago, para usar en pruebas y en la colección de Postman. */
  static String signatureFor(String dataId, String requestId, String ts) {
    MercadoPagoSignatureValidator validator = new MercadoPagoSignatureValidator(props(SECRET));
    return "ts=" + ts + ",v1=" + validator.sign(MercadoPagoSignatureValidator.manifest(dataId, requestId, ts));
  }

  private final MercadoPagoSignatureValidator validator = new MercadoPagoSignatureValidator(props(SECRET));

  @Test void manifiestoSigueElFormatoDeMercadoPago() {
    assertEquals("id:123456;request-id:req-1;ts:1704908010;", MercadoPagoSignatureValidator.manifest("123456", "req-1", "1704908010"));
    assertEquals("id:abc123;ts:1;", MercadoPagoSignatureValidator.manifest("ABC123", null, "1"));
  }

  @Test void aceptaFirmaValida() {
    assertDoesNotThrow(() -> validator.validate(signatureFor("123456", "req-1", "1704908010"), "req-1", "123456"));
  }

  @Test void rechazaFirmaAlteradaAusenteOMalFormada() {
    String valid = signatureFor("123456", "req-1", "1704908010");
    assertThrows(InvalidWebhookSignatureException.class, () -> validator.validate(valid, "req-1", "999999"));
    assertThrows(InvalidWebhookSignatureException.class, () -> validator.validate(null, "req-1", "123456"));
    assertThrows(InvalidWebhookSignatureException.class, () -> validator.validate("v1=abc", "req-1", "123456"));
  }

  @Test void sinSecretoConfiguradoNoAceptaNada() {
    var unconfigured = new MercadoPagoSignatureValidator(props(""));
    assertThrows(DependencyUnavailableException.class, () -> unconfigured.validate("ts=1,v1=abc", "r", "1"));
  }
}
