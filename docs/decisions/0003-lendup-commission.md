# ADR 0003: comisión de LendUp

Estado: aceptada.

## Contexto

El informe asigna a Pagos el cálculo y registro de la comisión de LendUp por préstamo y la distribución entre LendUp y el prestamista (RF37, patrón 25 `PoliticaDeComision`). No fijaba el porcentaje ni qué pasa con la comisión cuando se cancela.

## Decisión

- `PoliticaDeComision` (Strategy) calcula un porcentaje de la tarifa, configurable con `LENDUP_COMMISSION_PERCENT` (18 por defecto, definido por el equipo).
- Se **descuenta de lo que recibe el prestamista**: el prestatario paga la tarifa y la garantía, sin recargo.
- Se registra en `comision_lendup` de la transacción `LIBERACION_TARIFA` y viaja en `TarifaLiberada` con `monto_neto_prestamista`.
- `GET /prestamos/{id}/desglose-pago` muestra cada concepto antes de pagar y no genera transacciones (US32).
- Si la reserva se cancela no hay liberación, así que no hay comisión: se reembolsa la tarifa completa (RF46, RF51).

## Consecuencias

El frontend calcula en `lib/business-rules.ts` una comisión como recargo al prestatario, pero con datos de la API la deja en 0, así que el monto que envía coincide con el que valida Pagos. Si la muestra, debe tomarla de `desglose-pago`.

Cambiar la regla (monto fijo, escalas o comisión al prestatario) es una nueva implementación de `PoliticaDeComision`. La comisión del proveedor se conoce recién al confirmar el pago; hasta entonces va en `null`.
