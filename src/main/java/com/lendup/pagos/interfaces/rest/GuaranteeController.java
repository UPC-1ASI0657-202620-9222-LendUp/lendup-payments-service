package com.lendup.pagos.interfaces.rest;
import com.lendup.pagos.application.port.in.GuaranteeUseCases; import com.lendup.pagos.application.port.in.PaymentViews.ConstituirGarantiaCommand; import com.lendup.pagos.config.security.ActorContext; import com.lendup.pagos.interfaces.rest.dto.RequestDtos.GuaranteeRequest; import com.lendup.pagos.interfaces.rest.dto.ResponseDtos.TransaccionResponse; import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag; import jakarta.validation.Valid; import org.slf4j.MDC; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
/** Guarantee Controller (Fig. 18): constitución de la garantía monetaria. */
@RestController @RequestMapping("/api/v1") @Tag(name="Garantías")
public class GuaranteeController {
  private final GuaranteeUseCases useCases; private final ActorContext actor;
  public GuaranteeController(GuaranteeUseCases u,ActorContext a){useCases=u;actor=a;}
  @PostMapping("/garantias") @Operation(summary="Constituir garantía",description="Crea la garantía y su cobro PENDIENTE y devuelve checkout_url. 409 si ya existe o si el préstamo no requiere garantía.")
  ResponseEntity<TransaccionResponse> guarantee(@Valid @RequestBody GuaranteeRequest b){return ResponseEntity.status(HttpStatus.CREATED).body(TransaccionResponse.from(useCases.constituirGarantia(actor.userId(),new ConstituirGarantiaCommand(b.prestamo_id(),b.medio_pago_seleccionado(),b.clave_idempotencia()),MDC.get("correlation_id"))));}
}
