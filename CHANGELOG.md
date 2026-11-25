# Changelog

## Unreleased

### Corregido
- Eventos perdidos: el Outbox publicaba sin `mandatory` y marcaba como publicado un evento que ninguna cola recibía (el broker "se veía vacío"). Ahora queda pendiente y se reintenta con espera creciente (`next_attempt_at`, migración V2). Un tipo de evento desconocido ya no se publica con una routing key inventada.
- Colección Postman: los eventos simulados ahora respetan las variables de entorno (`pm.variables`); la petición "Sin token (401)" ya no envía el token.
- Colección Postman: "Pagar tarifa" usaba una clave de idempotencia fija y, desde la segunda corrida, Pagos respondía 409 "La clave de pago ya fue utilizada". Ahora la clave depende del préstamo (`pago-tarifa-{{prestamo_id}}`).

### Agregado
- Modo `MERCADOPAGO_MODE=simulado` con simulador de Checkout Pro, para que el equipo pruebe pagos, webhook, reembolsos y eventos sin credenciales de Mercado Pago.
- Postman: login de Firebase, `usuario.id` desde Identidad, cola espía para ver los eventos en el broker y carpeta del simulador.
- `.env.example` alineado al equipo: Firebase `lendup-desarrollo`, Identidad en `:8081`, frontend en `:3000`. Puertos locales que no chocan con otros servicios, y opción de broker compartido.


- Servicio inicial de Pagos y Garantías extraído del monolito con arquitectura hexagonal y los componentes de la Fig. 18.
- Base de datos propia `lendup_pagos` (Flyway): `garantias`, `transacciones_economicas` (estados de la Fig. 11), proyección `prestamos_pagables`, `notificaciones_proveedor`, outbox, inbox y auditoría de solo inserción.
- Endpoints compatibles con el frontend: medios de pago (`MERCADO_PAGO`), pago de tarifa y extensión, constitución de garantía con `checkout_url`, consulta de transacciones y webhook público con firma.
- Integración con Mercado Pago Checkout Pro (SDK oficial), consulta del pago como fuente de verdad, conciliación programada de pendientes y reembolsos idempotentes; timeout y circuit breaker con Resilience4j.
- Flujos de liberación de tarifa, devolución total, retención por incidencia (`LiquidacionDeGarantiaService`) y reembolso por cancelación (saga coreografiada).
- Eventos `pagos.*.v1` por transactional outbox con publisher confirms; consumo con inbox, reintentos y DLQ; contratos en `contracts/events/v1`.
- Pruebas unitarias, de contrato con el frontend, WireMock para Mercado Pago, Testcontainers (MySQL, RabbitMQ) y escenarios Cucumber en español (US17, US33, US34, US36, US41).
- Colección Postman, OpenAPI, ADRs y workflows de CI y publicación en Docker Hub.
