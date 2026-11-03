package com.lendup.pagos.interfaces.rest;
import com.lendup.pagos.infrastructure.webhook.WebhookIdempotencyHandler; import com.lendup.pagos.interfaces.rest.dto.ResponseDtos.WebhookResponse; import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.security.SecurityRequirements; import io.swagger.v3.oas.annotations.tags.Tag; import java.util.Map; import org.slf4j.MDC; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
/** Mercado Pago Webhook Controller (Fig. 18): público, autenticado por la firma x-signature. */
@RestController @RequestMapping("/api/v1") @Tag(name="Webhooks")
public class MercadoPagoWebhookController {
  private final WebhookIdempotencyHandler handler;
  public MercadoPagoWebhookController(WebhookIdempotencyHandler h){handler=h;}
  @PostMapping("/webhooks/mercado-pago") @SecurityRequirements @Operation(summary="Recibir notificación de Mercado Pago",description="Valida x-signature, descarta duplicados y consulta el pago en Mercado Pago antes de cambiar estados.")
  ResponseEntity<WebhookResponse> webhook(@RequestHeader(name="x-signature",required=false) String signature,@RequestHeader(name="x-request-id",required=false) String requestId,
      @RequestParam(name="data.id",required=false) String dataId,@RequestParam(name="type",required=false) String type,@RequestBody(required=false) Map<String,Object> body){
    var result=handler.handle(signature,requestId,dataId,type,body,MDC.get("correlation_id"));
    return ResponseEntity.status(HttpStatus.CREATED).body(new WebhookResponse(result.proveedorEventoId(),result.estado()));
  }
}
