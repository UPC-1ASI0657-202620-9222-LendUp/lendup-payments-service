package com.lendup.pagos.interfaces.rest;
import com.lendup.pagos.application.port.in.PaymentUseCases; import com.lendup.pagos.application.port.in.PaymentViews.CrearPagoCommand; import com.lendup.pagos.config.security.ActorContext; import com.lendup.pagos.interfaces.rest.dto.RequestDtos.PaymentRequest; import com.lendup.pagos.interfaces.rest.dto.ResponseDtos.*; import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag; import jakarta.validation.Valid; import java.util.List; import org.slf4j.MDC; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
/** Payment Controller (Fig. 18): medios de pago y pago de tarifa o extensión con Checkout Pro. */
@RestController @RequestMapping("/api/v1") @Tag(name="Pagos")
public class PaymentController {
  private final PaymentUseCases useCases; private final ActorContext actor;
  public PaymentController(PaymentUseCases u,ActorContext a){useCases=u;actor=a;}
  @GetMapping("/medios-pago") @Operation(summary="Listar medios de pago habilitados")
  List<MedioPagoResponse> paymentMethods(){return useCases.listarMediosPago().stream().map(MedioPagoResponse::from).toList();}
  @PostMapping("/pagos") @Operation(summary="Iniciar pago de tarifa o extensión",description="Crea la transacción PENDIENTE y devuelve checkout_url de Mercado Pago. Solo el prestatario.")
  ResponseEntity<TransaccionResponse> pay(@Valid @RequestBody PaymentRequest b){return ResponseEntity.status(HttpStatus.CREATED).body(TransaccionResponse.from(useCases.crearPago(actor.userId(),new CrearPagoCommand(b.prestamo_id(),b.cambio_fecha_prestamo_id(),b.tipo(),b.monto(),b.medio_pago_seleccionado(),b.clave_idempotencia()),MDC.get("correlation_id"))));}
}
