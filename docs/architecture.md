# Arquitectura

## Alcance

Pagos y Garantías posee `garantias`, `transacciones_economicas`, la proyección `prestamos_pagables`, `notificaciones_proveedor`, outbox (`outbox_events`), inbox (`processed_events`) y auditoría (`registro_auditoria`) en su propia base `lendup_pagos`. Préstamos, reservas, usuarios, cambios de fecha e incidencias son referencias externas por UUID: no hay claves foráneas hacia otros servicios ni lecturas de otros esquemas. Es el único servicio que conoce a Mercado Pago (CD-09).

## Componentes (Fig. 18)

| Componente del diagrama | Clase |
|---|---|
| Payment Controller | `interfaces/rest/PaymentController` (`/medios-pago`, `/pagos`) |
| Guarantee Controller | `interfaces/rest/GuaranteeController` (`/garantias`) |
| Transaction Query Controller | `interfaces/rest/TransactionQueryController` |
| Mercado Pago Webhook Controller | `interfaces/rest/MercadoPagoWebhookController` |
| Webhook Idempotency Handler | `infrastructure/webhook/WebhookIdempotencyHandler` (+ `MercadoPagoSignatureValidator`) |
| Payment Application Service | `application/service/PaymentApplicationService` |
| Guarantee Application Service | `application/service/GuaranteeApplicationService` |
| Economic Event Consumer | `infrastructure/messaging/EconomicEventConsumer` |
| Garantía Aggregate | `domain/model/Garantia` |
| LiquidacionDeGarantiaService | `domain/service/LiquidacionDeGarantiaService` |
| Mercado Pago Adapter (ACL) | `infrastructure/client/MercadoPagoAdapter` (puerto `PaymentGatewayPort`) |
| Transaction Repository | puerto `TransactionRepository`, adaptador `JpaTransactionRepositoryAdapter` |
| Outbox Event Publisher | `OutboxJpaAdapter` + `infrastructure/messaging/OutboxRelay` |

Componentes adicionales que no aparecen en la figura: `CheckoutCoordinator` (crea la preferencia fuera de la transacción de BD), `EconomicEventRecorder` (arma los eventos del outbox), `PendingReconciliationScheduler` (respaldo del webhook, CD-09) y la proyección `prestamos_pagables`, alimentada también por `reservas.confirmada.v1`.

Capas: `domain` (agregados, enums de la Fig. 11, `PoliticaDeCosto` como Strategy, servicio de dominio), `application` (casos de uso y puertos), `infrastructure` (JPA/MySQL, RabbitMQ, SDK de Mercado Pago, REST a Identidad, scheduler) e `interfaces/rest` (DTOs y errores).

## Flujos

1. **Cobro (tarifa, extensión, garantía):** valida participación y monto contra la proyección → transacción `PENDIENTE` (y garantía `PENDIENTE`) en una transacción local → preferencia de Checkout Pro fuera de la transacción → guarda `checkout_url`. Idempotente por `clave_idempotencia`.
2. **Webhook:** firma → registro/dedupe en `notificaciones_proveedor` → consulta del pago en Mercado Pago → en una sola transacción: cambio de estado, auditoría y outbox (`PagoConfirmado`, `PagoRechazado`, `GarantiaConstituida`).
3. **Conciliación (CD-09):** cada `PENDING_RECONCILIATION_DELAY_MS` busca por `external_reference` los cobros `PENDIENTE` más antiguos que `PENDING_RECONCILIATION_MIN_AGE_MS` y reintenta reembolsos `PENDIENTE`. Ante timeout o circuito abierto no cambia nada (RNF14).
4. **Préstamo activado:** `LIBERACION_TARIFA` confirmada por cada pago de tarifa/extensión → `TarifaLiberada` (registro contable, ADR 0001).
5. **Préstamo finalizado:** `DEVOLUCION_GARANTIA` del saldo → reembolso en Mercado Pago → `GarantiaDevuelta`.
6. **Incidencia resuelta:** `LiquidacionDeGarantiaService` → `RETENCION_GARANTIA` + `GarantiaRetenida`; si queda saldo, devolución parcial → `GarantiaDevuelta`.
7. **Reserva cancelada (saga):** reembolso de tarifas confirmadas (`REEMBOLSO_TARIFA` → `ReembolsoProcesado`), devolución de garantía constituida y cancelación de cobros/garantías pendientes. Un pago que llega después de cancelar, o un segundo pago de tarifa, se reembolsa automáticamente.

Los reembolsos se crean `PENDIENTE` en la transacción del evento y se envían a Mercado Pago después del commit con `X-Idempotency-Key` = `clave_idempotencia` (`reembolso:<pago>`, `devolucion-garantia:<garantia>:<motivo>`). Si el proveedor no responde quedan pendientes y la conciliación reintenta con la misma clave; si los rechaza quedan `FALLIDA` y se auditan para revisión manual.

## Consistencia

Transactional Outbox con publisher confirms (como Reservas). Inbox por `event_id` en la misma transacción del cambio; reintentos (3) y DLQ `payments.events.v1.dlq`. RabbitMQ entrega al menos una vez; además de la inbox, cada operación económica tiene una clave única, así que un evento repetido no genera instrucciones duplicadas (RNF13). `aggregate_version` de los eventos emitidos es la marca de tiempo en milisegundos.

## Seguridad

Firebase ID token validado en el servicio (revocación y `email_verified`), UID → `usuario_id` por `IdentidadPort`, participación verificada contra `prestamos_pagables`. El webhook es la única ruta pública y exige firma. LendUp no guarda datos de tarjeta (RNF11): solo id de pago, comisión, monto y estado. El SDK solo permite HTTPS hacia Mercado Pago; `http://` se acepta únicamente para simuladores locales (`MERCADOPAGO_API_BASE_URL`).

## Desvíos respecto al informe

- Tablas técnicas con los nombres de Reservas (`outbox_events`, `processed_events`) en vez de `outbox_eventos`/`mensajes_procesados`.
- Columna adicional `checkout_url` en `transacciones_economicas`; identificadores `BINARY(16)`.
- `RETENIDA_POR_INCIDENCIA` existe pero no se usa: Pagos no consume `IncidenciaReportada`.
- Los reembolsos rechazados quedan `FALLIDA` (no existe `REQUIERE_REVISION` en la Fig. 11).
