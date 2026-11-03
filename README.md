# LendUp Payments Service

Microservicio Java 21/Spring Boot 4 para el bounded context de Pagos y Garantías. Conserva las cinco rutas de pagos del monolito consumidas por el frontend (`/medios-pago`, `/pagos`, `/garantias`, `/prestamos/{id}/transacciones` y el webhook de Mercado Pago), agrega `checkout_url` a las respuestas de cobro, usa MySQL/InnoDB con Flyway, Firebase ID tokens, RabbitMQ con transactional outbox e inbox, y Mercado Pago Checkout Pro mediante el SDK oficial para Java.

## Estado y límites

- **Mercado Pago:** cobros con Checkout Pro (preferencia con `external_reference` = id de la transacción), webhook con firma `x-signature`, consulta del pago como fuente de verdad, conciliación programada de pendientes y reembolsos totales o parciales con `X-Idempotency-Key`. Timeout por llamada y circuit breaker de Resilience4j. El pago real al prestamista (marketplace) queda fuera de alcance: la liberación de tarifa es un registro contable (ver [ADR 0001](docs/decisions/0001-mercado-pago-checkout.md)).
- **Garantía:** se cobra con Checkout Pro y se reembolsa total o parcialmente ([ADR 0002](docs/decisions/0002-guarantee-handling.md)).
- **Contratos pendientes:** Pagos necesita eventos de Préstamos (préstamo creado con `prestamo_id` + `reserva_id`, activado, finalizado, extensión aceptada) y de Evidencias (`IncidenciaResuelta`). El monolito aún no los publica; las routing keys son configurables y los payloads esperados están en `contracts/events/v1/consumed-events.schema.json`. Sin el evento "préstamo creado", `POST /pagos` y `POST /garantias` responden `404` porque la proyección no conoce el `prestamo_id`.
- **Identidad:** UID de Firebase → `usuario_id` mediante `IDENTITY_FIREBASE_USER_URI_TEMPLATE`. Sin configurar, las rutas autenticadas responden `503`.

Detalles: [arquitectura](docs/architecture.md), [API](docs/api-contract.md), [eventos](docs/events.md), [pruebas](docs/testing.md), [AWS](docs/aws-handoff.md), [migración](docs/migration-from-monolith.md).

## Inicio local

Requisitos: JDK 21, Docker con Compose v2, credencial Firebase de desarrollo y credenciales de prueba de Mercado Pago.

```powershell
Copy-Item .env.example .env
New-Item -ItemType Directory -Force .secrets
# Colocar una credencial de desarrollo en .secrets/firebase-service-account.json; nunca versionarla.
# Completar MERCADOPAGO_ACCESS_TOKEN y MERCADOPAGO_WEBHOOK_SECRET en .env.
.\mvnw.cmd clean verify
docker compose up --build
```

API: `http://localhost:8082/api/v1`; readiness: `http://localhost:8082/actuator/health/readiness`. MySQL queda en `127.0.0.1:3308`, RabbitMQ en `127.0.0.1:5673` y su consola en `http://localhost:15673` (puertos distintos a Reservas para poder levantar ambos). Para integrar con Reservas en local, apuntar `RABBITMQ_HOST`/`RABBITMQ_PORT` de ambos servicios al mismo broker.

Swagger está deshabilitado por defecto; para desarrollo local: `SPRINGDOC_ENABLED=true` (requiere Firebase salvo `SWAGGER_PUBLIC=true`, que no debe usarse en producción). El contrato también está en [`contracts/openapi/payments-v1.yaml`](contracts/openapi/payments-v1.yaml).

### Webhook de Mercado Pago en local

Mercado Pago necesita una URL pública HTTPS. Exponer el puerto 8082 con un túnel (por ejemplo `ngrok http 8082`), poner la URL en `MERCADOPAGO_NOTIFICATION_URL` (`https://<tunel>/api/v1/webhooks/mercado-pago`), configurar la misma URL en *Tus integraciones → Webhooks* del panel de Mercado Pago (evento *Pagos*) y copiar la clave secreta a `MERCADOPAGO_WEBHOOK_SECRET`. Si el webhook no llega, la conciliación programada (`PENDING_RECONCILIATION_DELAY_MS`) consulta los pendientes.

Para ejecutar solo el artefacto:

```powershell
.\mvnw.cmd clean package
java -jar target\lendup-payments-service-0.1.0-SNAPSHOT.jar
```

Los periodos `desde`/`hasta` son hora de negocio `America/Lima` sin offset (como Reservas). Timestamps `_en`, auditoría y eventos se emiten en UTC.

## Imagen

```powershell
docker build --platform linux/amd64 -t lendup-payments-service:local .
docker run --rm -p 8082:8082 --env-file .env -v "${PWD}\.secrets\firebase-service-account.json:/run/secrets/firebase-service-account.json:ro" lendup-payments-service:local
docker tag lendup-payments-service:local DOCKERHUB_USERNAME/lendup-payments-service:0.1.0
docker login -u DOCKERHUB_USERNAME
docker push DOCKERHUB_USERNAME/lendup-payments-service:0.1.0
```

No ejecutar `docker push` con un namespace literal; sustituir `DOCKERHUB_USERNAME`. La publicación normal se realiza por tag `v*.*.*` mediante GitHub Actions (`.github/workflows/docker-publish.yml`, requiere la variable `DOCKERHUB_USERNAME` y el secreto `DOCKERHUB_TOKEN`).
