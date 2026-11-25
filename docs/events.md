# Eventos

Exchange durable: `lendup.domain.events` (topic). DLX: `lendup.domain.events.dlx`. Cola consumidora: `payments.events.v1`; DLQ: `payments.events.v1.dlq`. Envelope: `event_id`, `event_type`, `event_version`, `aggregate_version`, `aggregate_id`, `occurred_at` UTC, `correlation_id`, `payload`. Schemas y ejemplos en `contracts/events/v1`.

## Publicados

| Evento | Routing key | Cuándo | aggregate_id |
|---|---|---|---|
| `PagoConfirmado` | `pagos.pago-confirmado.v1` | Mercado Pago aprueba un pago de tarifa o extensión | transacción |
| `PagoRechazado` | `pagos.pago-rechazado.v1` | Mercado Pago rechaza o cancela un cobro (incluida la garantía) | transacción |
| `GarantiaConstituida` | `pagos.garantia-constituida.v1` | Mercado Pago aprueba el cobro de la garantía | garantía |
| `TarifaLiberada` | `pagos.tarifa-liberada.v1` | Préstamo activado (o extensión pagada con préstamo activo) | transacción |
| `GarantiaDevuelta` | `pagos.garantia-devuelta.v1` | Mercado Pago confirma una devolución de garantía | garantía |
| `GarantiaRetenida` | `pagos.garantia-retenida.v1` | Incidencia resuelta con afectación | garantía |
| `ReembolsoProcesado` | `pagos.reembolso-procesado.v1` | Mercado Pago confirma un reembolso de tarifa | transacción |

### Entrega garantizada (Outbox + mandatory)

El relay publica con *publisher confirms* y `mandatory=true` (igual que Reservas). Un evento solo se marca publicado si RabbitMQ lo confirma **y** lo entregó al menos a una cola. Si ningún servicio tiene una cola suscrita a esa routing key, el broker devuelve el mensaje. En ese caso el evento sigue pendiente en `outbox_events`, con `last_error = "Ninguna cola suscrita a ..."`, y se reintenta con espera creciente (`next_attempt_at`, hasta `OUTBOX_MAX_ATTEMPTS`).

Antes, en ese caso el evento se marcaba publicado y se perdía. Por eso el broker "se veía vacío": un exchange topic **no guarda mensajes**, solo los reparte a las colas que existan en ese momento. Para ver los eventos sin levantar otros servicios, la colección Postman crea una cola espía (`debug.lendup.eventos`, enlazada con `#`).

Los payloads incluyen `prestamo_id`, `reserva_id`, `prestatario_usuario_id` y `prestamista_usuario_id` para que Notificaciones y Préstamos no tengan que consultar a Pagos.

## Consumidos

| Evento | Routing key | Productor | Estado | Uso |
|---|---|---|---|---|
| `ReservaConfirmada` | `reservas.confirmada.v1` | Reservas | Disponible | Crea la proyección `prestamos_pagables` (sin `prestamo_id`) |
| `ReservaCancelada` | `reservas.cancelada.v1` | Reservas | Disponible | Reembolsos de la saga de cancelación |
| `PrestamoCreado` | `LOAN_CREATED_ROUTING_KEY` (`prestamos.creado.v1`) | Préstamos (monolito) | **Pendiente** | Completa `prestamo_id` |
| `PrestamoActivado` | `LOAN_ACTIVATED_ROUTING_KEY` (`prestamos.activado.v1`) | Préstamos | **Pendiente** | Liberación de tarifa |
| `ExtensionAceptada` | `EXTENSION_ACCEPTED_ROUTING_KEY` (`prestamos.extension-aceptada.v1`) | Préstamos | **Pendiente** | Habilita `PAGO_EXTENSION` |
| `PrestamoFinalizado` | `LOAN_FINISHED_ROUTING_KEY` (`prestamos.finalizado.v1`) | Préstamos | **Pendiente** | Devolución de garantía |
| `IncidenciaResuelta` | `INCIDENT_RESOLVED_ROUTING_KEY` (`evidencias.incidencia-resuelta.v1`) | Evidencias (monolito) | **Pendiente** | Liquidación de garantía |

El consumidor despacha por routing key (no por `event_type`), por eso basta con configurar la routing key real cuando el productor exista. Los productores marcados como pendientes **aún no existen**: el payload esperado está en `consumed-events.schema.json` y se prueba con los mensajes de `examples.json` (`EconomicEventConsumerTest`, escenarios Cucumber y la carpeta "Eventos simulados" de la colección Postman, que publica en RabbitMQ con su API de administración).

`PrestamoCreado` debe llegar después de `ReservaConfirmada` (el préstamo nace de esa reserva). Si llega antes, el mensaje falla, se reintenta y termina en la DLQ; se puede reenviar desde la consola de RabbitMQ.

## Consumidores esperados de los eventos de Pagos

- Préstamos: `PagoConfirmado` y `GarantiaConstituida` para habilitar la entrega (RF19); `PagoConfirmado` con `cambio_fecha_prestamo_id` para aplicar la extensión; `PagoRechazado`.
- Notificaciones: todos los `pagos.*.v1`.

Publicar no constituye una integración end-to-end: los consumidores del monolito todavía no implementan estos contratos.
