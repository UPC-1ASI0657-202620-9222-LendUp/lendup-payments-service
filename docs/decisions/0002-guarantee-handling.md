# ADR 0002: garantía cobrada y reembolsada

Estado: aceptada.

## Contexto

La garantía monetaria (US17, US36, RF41) debe constituirse antes de la entrega y devolverse total o parcialmente según las incidencias. Mercado Pago ofrece "reserva de fondos" (pago con captura diferida), pero exige tokenizar la tarjeta en el frontend (Checkout API/Bricks) y la autorización vence en pocos días, menos que muchos préstamos.

## Decisión

La garantía se **cobra con Checkout Pro** como un pago normal (`CONSTITUCION_GARANTIA`) y luego se **reembolsa**:

- Préstamo finalizado sin saldo afectado: reembolso total → `DEVOLUCION_GARANTIA`, garantía `DEVUELTA`.
- Incidencia resuelta: `LiquidacionDeGarantiaService` calcula retención y saldo con las reglas del monolito (`SIN_AFECTACION` retiene 0; `AFECTACION_PARCIAL` retiene el monto informado, limitado al saldo; `AFECTACION_TOTAL` retiene todo el saldo). La retención se registra como `RETENCION_GARANTIA` confirmada (el dinero ya está en la cuenta de LendUp) y el saldo se devuelve con un reembolso parcial. Estados `AFECTADA_PARCIALMENTE` o `AFECTADA_TOTALMENTE`.
- Reserva cancelada antes de la entrega: devolución total; una garantía aún `PENDIENTE` pasa a `CANCELADA`.
- Cada devolución es idempotente (`devolucion-garantia:<garantia>:<motivo>[:<incidencia>]`) y descuenta lo que ya está en trámite, de modo que nunca se devuelve más que el saldo.

La captura diferida queda como mejora futura.

## Consecuencias

- El prestatario ve el cargo completo mientras dure el préstamo y la comisión del proveedor del cobro no se recupera con el reembolso; debe informarse en el desglose (RF37).
- El saldo se devuelve al resolver cada incidencia (flujo indicado para el sprint). Una incidencia posterior solo puede afectar el saldo no devuelto; si el negocio requiere retener la garantía completa hasta finalizar, basta con mover la devolución del saldo al evento `PrestamoFinalizado`.
- El estado `RETENIDA_POR_INCIDENCIA` queda en el catálogo pero no se usa, porque Pagos no consume `IncidenciaReportada`.
