# Migración desde el monolito

No hay lectura cross-schema en runtime. El cutover es un procedimiento separado:

1. Inventariar y respaldar `garantias`, `transacciones_economicas` y `webhook_events` del monolito. En el Sprint 1 el monolito no procesaba pagos reales: las transacciones quedaban `PENDIENTE_LIBERACION` y las garantías `CONSTITUIDA` sin dinero de por medio. Decidir con el equipo si esos registros se migran como históricos o se descartan; no deben convertirse en `CONFIRMADA` porque no existe un pago en Mercado Pago que los respalde.
2. Convertir ids `CHAR(36)` a `BINARY(16)` (`UUID_TO_BIN`) y mapear estados al catálogo de la Fig. 11.
3. Poblar `prestamos_pagables` desde `reservas` + `prestamos` del monolito (reserva_id, prestamo_id, participantes, `entrega_programada_en`/`devolucion_original_en` como `desde`/`hasta`, tarifa, garantía, moneda, estado). A partir del cutover la proyección se mantiene solo con eventos.
4. `webhook_events` no se migra: nunca validó firmas ni cambió estados.
5. Detener las escrituras del monolito en estas tablas: `PagosApplicationService` deja de usarse, `IncidentsService.resolve` deja de actualizar `garantias` y publica `IncidenciaResuelta`, y Préstamos deja de leer `pago_tarifa_confirmado_en`/`garantia_constituida_en` para consumir `PagoConfirmado`/`GarantiaConstituida`.
6. Cambiar el Gateway a este servicio. Mantener rollback por routing durante la ventana, sin doble escritura.

Diferencias de comportamiento a comunicar al frontend: `MERCADO_PAGO` en vez de `PAGO_EN_LINEA`, `checkout_url`, nuevos estados y que el pago de tarifa vuelve a habilitarse porque ahora hay un monto autoritativo (tarifa × bloques de 24 h).
