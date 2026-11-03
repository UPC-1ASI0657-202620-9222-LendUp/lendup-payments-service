package com.lendup.pagos.interfaces.rest.dto;
import io.swagger.v3.oas.annotations.media.Schema; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.UUID;
/** Cuerpos de solicitud con el mismo contrato que el monolito (snake_case). */
public final class RequestDtos { private RequestDtos(){}
  @Schema(name="PaymentRequest")
  public record PaymentRequest(
    @Schema(example="UUID_PRESTAMO", requiredMode=Schema.RequiredMode.REQUIRED) @NotNull UUID prestamo_id,
    @Schema(description="Obligatorio para PAGO_EXTENSION") UUID cambio_fecha_prestamo_id,
    @Schema(allowableValues={"PAGO_TARIFA","PAGO_EXTENSION"}, requiredMode=Schema.RequiredMode.REQUIRED) @NotBlank @Pattern(regexp="PAGO_TARIFA|PAGO_EXTENSION") String tipo,
    @Schema(example="20.00", requiredMode=Schema.RequiredMode.REQUIRED) @NotNull @Positive @Digits(integer=10,fraction=2) BigDecimal monto,
    @Schema(example="MERCADO_PAGO", requiredMode=Schema.RequiredMode.REQUIRED) @NotBlank String medio_pago_seleccionado,
    @Schema(example="pago-UUID_UNICO", requiredMode=Schema.RequiredMode.REQUIRED) @NotBlank @Size(max=150) String clave_idempotencia){}
  @Schema(name="GuaranteeRequest")
  public record GuaranteeRequest(
    @Schema(example="UUID_PRESTAMO", requiredMode=Schema.RequiredMode.REQUIRED) @NotNull UUID prestamo_id,
    @Schema(example="MERCADO_PAGO") String medio_pago_seleccionado,
    @Schema(example="garantia-UUID_UNICO") @Size(max=150) String clave_idempotencia){}
}
