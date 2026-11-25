# LendUp Payments Service

Microservicio Java 21/Spring Boot 4 para el bounded context de Pagos y Garantías. Conserva las cinco rutas de pagos del monolito consumidas por el frontend (`/medios-pago`, `/pagos`, `/garantias`, `/prestamos/{id}/transacciones` y el webhook de Mercado Pago), agrega `checkout_url` a las respuestas de cobro, usa MySQL/InnoDB con Flyway, Firebase ID tokens, RabbitMQ con transactional outbox e inbox, y Mercado Pago Checkout Pro mediante el SDK oficial para Java.

## Estado y límites

- **Mercado Pago:** cobros con Checkout Pro (preferencia con `external_reference` = id de la transacción), webhook con firma `x-signature`, consulta del pago como fuente de verdad, conciliación programada de pendientes y reembolsos totales o parciales con `X-Idempotency-Key`. Timeout por llamada y circuit breaker de Resilience4j. El pago real al prestamista (marketplace) queda fuera de alcance: la liberación de tarifa es un registro contable (ver [ADR 0001](docs/decisions/0001-mercado-pago-checkout.md)).
- **Garantía:** se cobra con Checkout Pro y se reembolsa total o parcialmente ([ADR 0002](docs/decisions/0002-guarantee-handling.md)).
- **Contratos pendientes:** Pagos necesita eventos de Préstamos (préstamo creado con `prestamo_id` + `reserva_id`, activado, finalizado, extensión aceptada) y de Evidencias (`IncidenciaResuelta`). El monolito aún no los publica; las routing keys son configurables y los payloads esperados están en `contracts/events/v1/consumed-events.schema.json`. Sin el evento "préstamo creado", `POST /pagos` y `POST /garantias` responden `404` porque la proyección no conoce el `prestamo_id`.
- **Identidad:** UID de Firebase → `usuario_id` mediante `IDENTITY_FIREBASE_USER_URI_TEMPLATE`. Sin configurar, las rutas autenticadas responden `503`.

Detalles: [arquitectura](docs/architecture.md), [API](docs/api-contract.md), [eventos](docs/events.md), [pruebas](docs/testing.md), [AWS](docs/aws-handoff.md), [migración](docs/migration-from-monolith.md).

## Inicio local

Requisitos: JDK 21, Docker con Compose v2 y la credencial Firebase de desarrollo (proyecto `lendup-desarrollo`). Las credenciales de Mercado Pago son opcionales: sin ellas se usa el modo simulado.

```powershell
Copy-Item .env.example .env
New-Item -ItemType Directory -Force .secrets
# Colocar una credencial de desarrollo en .secrets/firebase-service-account.json; nunca versionarla.
# Por defecto MERCADOPAGO_MODE=simulado. Para el sandbox real: MERCADOPAGO_MODE=real + MERCADOPAGO_ACCESS_TOKEN y MERCADOPAGO_WEBHOOK_SECRET.
.\mvnw.cmd clean verify
docker compose up --build
```

API: `http://localhost:8082/api/v1`; readiness: `http://localhost:8082/actuator/health/readiness`. MySQL queda en `127.0.0.1:3311`, RabbitMQ en `127.0.0.1:5675` y su consola en `http://localhost:15675`. Son puertos distintos a los de Reservas, Catálogo, Identidad y Reputación; se cambian con `MYSQL_HOST_PORT`, `RABBITMQ_HOST_PORT` y `RABBITMQ_MANAGEMENT_HOST_PORT`. Catálogo y Reputación también usan el 8082: si los levantas juntos, cambia `SERVER_PORT`.

### Probar sin llaves de Mercado Pago (modo simulado)

Con `MERCADOPAGO_MODE=simulado`, `POST /pagos` y `POST /garantias` devuelven un `checkout_url` que apunta a `http://localhost:8082/api/v1/simulador/mercado-pago/checkout/{transaccion_id}`, en vez de a Mercado Pago:

- **En el navegador**, esa página muestra los botones *Aprobar pago* y *Rechazar pago*, y luego redirige a la URL de retorno del frontend.
- **Desde Postman**, `POST {{checkout_url}}?resultado=aprobado` hace lo mismo y responde en JSON (`payment_id`, `estado_webhook`, `estado_transaccion`).

El pago simulado se procesa por el mismo camino que el webhook real:

1. Registro en `notificaciones_proveedor`.
2. Consulta del pago.
3. Cambio de estado.
4. Auditoría y outbox.
5. Evento `pagos.*.v1`.

Los reembolsos también se simulan. El simulador solo existe en modo simulado; con `MERCADOPAGO_MODE=real` (valor por defecto de la aplicación) esa ruta no existe. Nunca usar el modo simulado en producción.

### Datos previos que necesita un pago

`POST /pagos` y `POST /garantias` responden `404 Préstamo no encontrado` hasta que Pagos recibe dos eventos: `reservas.confirmada.v1` (Reservas) y `prestamos.creado.v1`. Ningún servicio publica todavía el segundo; es un contrato pendiente de Préstamos. Mientras tanto, la carpeta *1. Eventos simulados* de la colección Postman publica ambos en RabbitMQ.

El `prestatario_usuario_id` del evento debe ser tu `usuario.id` en Identidad (la carpeta *0b. Preparación* lo obtiene con `GET /estudiantes/me`). Si no, la respuesta es 403.

### Broker compartido (integración con el equipo)

Cada `compose.yaml` levanta su propio RabbitMQ para probar el servicio solo, pero dos brokers distintos nunca intercambian eventos. Para integrar, todos los servicios deben apuntar al mismo broker. En local puede ser el de Reservas (`localhost:5672`). En `.env`:

```
RABBITMQ_HOST=host.docker.internal
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<usuario de ese broker>
RABBITMQ_PASSWORD=<contraseña de ese broker>
```

Las peticiones de *Eventos simulados* responden `routed=true` solo si alguna cola recibió el mensaje. Si sale `routed=false`, el servicio que debía escucharlo no está conectado a ese broker.

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
