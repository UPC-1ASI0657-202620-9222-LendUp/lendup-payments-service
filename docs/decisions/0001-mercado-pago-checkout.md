# ADR 0001: Mercado Pago Checkout Pro con webhook y conciliación

Estado: aceptada.

## Contexto

R21 exige procesar tarifas, garantías, devoluciones y reembolsos con Mercado Pago sin almacenar datos financieros sensibles (RNF11). La SPA no tokeniza tarjetas hoy, y el webhook puede llegar tarde, repetido o no llegar (CD-09).

## Decisión

- Los cobros usan **Checkout Pro**: el servicio crea una preferencia con `external_reference` = id de la transacción y devuelve `checkout_url`; el prestatario paga en Mercado Pago y vuelve por `back_urls`. LendUp solo guarda id de pago, comisión, monto y estado.
- El **webhook** se autentica con `x-signature` (HMAC-SHA256), se deduplica en `notificaciones_proveedor` y nunca se confía en su cuerpo: el estado se consulta en `GET /v1/payments/{id}`.
- Una **conciliación programada** busca por `external_reference` los cobros `PENDIENTE` antiguos y reintenta reembolsos pendientes.
- Reembolsos con `POST /v1/payments/{id}/refunds` y `X-Idempotency-Key` estable por operación.
- Integración con el **SDK oficial para Java**, detrás del puerto `PaymentGatewayPort` (capa anticorrupción). Timeout por llamada y circuit breaker de Resilience4j; los reintentos internos del SDK se desactivan para que LendUp controle cuándo y con qué clave reintentar. Ante timeout no se asume éxito ni rechazo (RNF14).

## Fuera de alcance: pago al prestamista

Pagar la tarifa al prestamista requiere el **modo marketplace** de Mercado Pago (cada prestamista vincula su cuenta por OAuth y la preferencia indica `marketplace_fee`), o transferencias manuales. En este sprint, al activarse el préstamo se registra una transacción `LIBERACION_TARIFA` confirmada y se publica `TarifaLiberada` con el monto neto (monto − comisión del proveedor): es la liberación contable que habilita el pago, no una transferencia real. Implementar marketplace es una mejora futura que cambia solo el adaptador y el registro de cuentas de prestamistas.

## Consecuencias

Más estados intermedios (`PENDIENTE`) y dependencia de una URL pública HTTPS para el webhook; la conciliación cubre su pérdida. Un mismo checkout puede producir varios intentos de pago: una transacción `RECHAZADA` puede pasar a `CONFIRMADA` si un intento posterior se aprueba; un segundo pago de tarifa aprobado se reembolsa automáticamente.
