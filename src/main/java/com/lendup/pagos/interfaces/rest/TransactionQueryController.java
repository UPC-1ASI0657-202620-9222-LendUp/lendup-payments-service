package com.lendup.pagos.interfaces.rest;
import com.lendup.pagos.application.port.in.PaymentUseCases; import com.lendup.pagos.config.security.ActorContext; import com.lendup.pagos.interfaces.rest.dto.ResponseDtos.TransaccionResponse; import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag; import java.util.List; import java.util.UUID; import org.springframework.web.bind.annotation.*;
/** Transaction Query Controller (Fig. 18): estado e historial de transacciones de un préstamo. */
@RestController @RequestMapping("/api/v1") @Tag(name="Transacciones")
public class TransactionQueryController {
  private final PaymentUseCases useCases; private final ActorContext actor;
  public TransactionQueryController(PaymentUseCases u,ActorContext a){useCases=u;actor=a;}
  @GetMapping("/prestamos/{id}/transacciones") @Operation(summary="Listar transacciones del préstamo",description="Solo participantes del préstamo.")
  List<TransaccionResponse> transactions(@PathVariable UUID id){return useCases.listarTransacciones(actor.userId(),id).stream().map(TransaccionResponse::from).toList();}
}
