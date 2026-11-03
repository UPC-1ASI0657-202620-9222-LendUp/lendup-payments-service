package com.lendup.pagos.infrastructure.webhook;

import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.exception.InvalidWebhookSignatureException;
import com.lendup.pagos.config.IntegrationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Valida el header x-signature de Mercado Pago ("ts=...,v1=..."). El manifiesto firmado con HMAC-SHA256 es
 * "id:{data.id};request-id:{x-request-id};ts:{ts};"; si falta data.id o x-request-id se omite esa parte.
 */
@Component
public class MercadoPagoSignatureValidator {
  private final String secret;

  public MercadoPagoSignatureValidator(IntegrationProperties properties) { this.secret = properties.mercadoPago().webhookSecret(); }

  public void validate(String signatureHeader, String requestId, String dataId) {
    if (secret == null || secret.isBlank()) throw new DependencyUnavailableException("MERCADOPAGO_WEBHOOK_SECRET no configurado");
    if (signatureHeader == null || signatureHeader.isBlank()) throw new InvalidWebhookSignatureException("Falta la firma x-signature");
    String ts = null, v1 = null;
    for (String part : signatureHeader.split(",")) {
      String[] kv = part.trim().split("=", 2);
      if (kv.length != 2) continue;
      if (kv[0].trim().equals("ts")) ts = kv[1].trim();
      if (kv[0].trim().equals("v1")) v1 = kv[1].trim();
    }
    if (ts == null || v1 == null) throw new InvalidWebhookSignatureException("Firma x-signature mal formada");
    String expected = sign(manifest(dataId, requestId, ts));
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), v1.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
      throw new InvalidWebhookSignatureException("Firma de Mercado Pago no válida");
    }
  }

  static String manifest(String dataId, String requestId, String ts) {
    StringBuilder manifest = new StringBuilder();
    if (dataId != null && !dataId.isBlank()) manifest.append("id:").append(dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase() : dataId).append(';');
    if (requestId != null && !requestId.isBlank()) manifest.append("request-id:").append(requestId).append(';');
    manifest.append("ts:").append(ts).append(';');
    return manifest.toString();
  }

  String sign(String manifest) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo calcular HMAC-SHA256", e);
    }
  }
}
