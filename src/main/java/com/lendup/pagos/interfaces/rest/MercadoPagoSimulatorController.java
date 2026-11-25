package com.lendup.pagos.interfaces.rest;

import com.lendup.pagos.application.exception.NotFoundException;
import com.lendup.pagos.application.port.out.TransactionRepository;
import com.lendup.pagos.config.IntegrationProperties;
import com.lendup.pagos.domain.model.TransaccionEconomica;
import com.lendup.pagos.infrastructure.client.SimulatedMercadoPagoAdapter;
import com.lendup.pagos.infrastructure.webhook.WebhookIdempotencyHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Simulador de Checkout Pro (solo con MERCADOPAGO_MODE=simulado). Reemplaza la página de Mercado Pago para probar
 * el flujo completo sin credenciales: el "pago" se registra en el adaptador simulado y se procesa por el mismo
 * camino del webhook (registro en notificaciones_proveedor, consulta del pago, auditoría y outbox).
 */
@RestController
@RequestMapping("/api/v1/simulador/mercado-pago")
@ConditionalOnProperty(name = "lendup.integrations.mercado-pago.mode", havingValue = "simulado")
@Tag(name = "Simulador de Mercado Pago (solo desarrollo)")
public class MercadoPagoSimulatorController {
  private final SimulatedMercadoPagoAdapter gateway;
  private final WebhookIdempotencyHandler webhook;
  private final TransactionRepository transactions;
  private final IntegrationProperties.MercadoPago config;

  public MercadoPagoSimulatorController(SimulatedMercadoPagoAdapter gateway, WebhookIdempotencyHandler webhook,
                                        TransactionRepository transactions, IntegrationProperties properties) {
    this.gateway = gateway; this.webhook = webhook; this.transactions = transactions; this.config = properties.mercadoPago();
  }

  @GetMapping(value = "/checkout/{transaccionId}", produces = MediaType.TEXT_HTML_VALUE)
  @SecurityRequirements
  @Operation(summary = "Página de checkout simulada", description = "Es la checkout_url que devuelven POST /pagos y POST /garantias en modo simulado.")
  String checkoutPage(@PathVariable UUID transaccionId) {
    TransaccionEconomica t = find(transaccionId);
    String action = HtmlUtils.htmlEscape(SimulatedMercadoPagoAdapter.CHECKOUT_PATH + transaccionId);
    return """
        <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Mercado Pago simulado</title><style>body{font-family:system-ui,sans-serif;max-width:420px;margin:48px auto;padding:0 16px}
        button{width:100%%;padding:12px;margin-top:12px;font-size:16px;border-radius:6px;border:0;cursor:pointer}
        .ok{background:#009ee3;color:#fff}.ko{background:#eee}</style></head><body>
        <h1>Mercado Pago (simulado)</h1><p>Entorno de desarrollo de LendUp: no se cobra dinero real.</p>
        <p><b>%s</b><br>Monto: %s %s<br>Estado actual: %s</p>
        <form method="post" action="%s"><input type="hidden" name="resultado" value="aprobado"><button class="ok">Aprobar pago</button></form>
        <form method="post" action="%s"><input type="hidden" name="resultado" value="rechazado"><button class="ko">Rechazar pago</button></form>
        </body></html>
        """.formatted(HtmlUtils.htmlEscape(t.tipo().name()), t.monto().toPlainString(), HtmlUtils.htmlEscape(t.moneda()),
        t.estado().name(), action, action);
  }

  @PostMapping("/checkout/{transaccionId}")
  @SecurityRequirements
  @Operation(summary = "Simular el resultado del pago", description = "resultado=aprobado|rechazado. Con notificar=false solo registra el pago, para probar luego el webhook firmado.")
  ResponseEntity<?> pay(@PathVariable UUID transaccionId,
                        @RequestParam(defaultValue = "aprobado") String resultado,
                        @RequestParam(defaultValue = "true") boolean notificar,
                        @RequestHeader(name = "Accept", required = false) String accept) {
    TransaccionEconomica t = find(transaccionId);
    boolean aprobado = !"rechazado".equalsIgnoreCase(resultado);
    var payment = gateway.registrarPago(t.id(), aprobado, t.monto(), t.moneda());
    String estadoWebhook = notificar ? webhook.handleSimulated(payment.paymentId(), MDC.get("correlation_id")).estado() : "NO_NOTIFICADO";
    if (accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
      String back = aprobado ? config.backUrlSuccess() : config.backUrlFailure();
      if (back != null && !back.isBlank()) {
        URI location = UriComponentsBuilder.fromUriString(back).queryParam("payment_id", payment.paymentId())
            .queryParam("status", aprobado ? "approved" : "rejected").queryParam("external_reference", t.id()).build().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
      }
      return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<!doctype html><html lang=\"es\"><meta charset=\"utf-8\"><body style=\"font-family:system-ui;margin:48px\">"
          + "<h1>Pago " + (aprobado ? "aprobado" : "rechazado") + " (simulado)</h1><p>payment_id: " + payment.paymentId() + "</p></body></html>");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("payment_id", payment.paymentId());
    body.put("transaccion_id", t.id());
    body.put("resultado", payment.resultado().name());
    body.put("estado_webhook", estadoWebhook);
    body.put("estado_transaccion", find(transaccionId).estado().name());
    return ResponseEntity.ok(body);
  }

  private TransaccionEconomica find(UUID id) {
    return transactions.findTransaccion(id).orElseThrow(() -> new NotFoundException("Transacción no encontrada"));
  }
}
