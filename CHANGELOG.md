# Changelog

## Unreleased

- Servicio inicial de Pagos y Garantías extraído del monolito con arquitectura hexagonal y los componentes de la Fig. 18.
- Base de datos propia `lendup_pagos` (Flyway): `garantias`, `transacciones_economicas` (estados de la Fig. 11), proyección `prestamos_pagables`, `notificaciones_proveedor`, outbox, inbox y auditoría de solo inserción.
- Endpoints compatibles con el frontend: medios de pago (`MERCADO_PAGO`), pago de tarifa y extensión, constitución de garantía con `checkout_url`, consulta de transacciones y webhook público con firma.
- Integración con Mercado Pago Checkout Pro (SDK oficial), consulta del pago como fuente de verdad, conciliación programada de pendientes y reembolsos idempotentes; timeout y circuit breaker con Resilience4j.
- Flujos de liberación de tarifa, devolución total, retención por incidencia (`LiquidacionDeGarantiaService`) y reembolso por cancelación (saga coreografiada).
- Eventos `pagos.*.v1` por transactional outbox con publisher confirms; consumo con inbox, reintentos y DLQ; contratos en `contracts/events/v1`.
- Pruebas unitarias, de contrato con el frontend, WireMock para Mercado Pago, Testcontainers (MySQL, RabbitMQ) y escenarios Cucumber en español (US17, US33, US34, US36, US41).
- Colección Postman, OpenAPI, ADRs y workflows de CI y publicación en Docker Hub.
