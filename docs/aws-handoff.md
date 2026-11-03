# Entrega a AWS

## Artefacto

Imagen: `DOCKERHUB_USERNAME/lendup-payments-service:<semver|sha>`. Plataforma `linux/amd64`; puerto 8082; liveness `/actuator/health/liveness`; readiness `/actuator/health/readiness`. Registrar tag y digest inmutable después de publicar.

## Configuración

Obligatorias: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `FIREBASE_PROJECT_ID`, ADC Firebase externo, `CORS_ORIGINS`, `IDENTITY_SERVICE_BASE_URL`, `IDENTITY_SERVICE_TOKEN`, `IDENTITY_FIREBASE_USER_URI_TEMPLATE`, `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET`, `MERCADOPAGO_NOTIFICATION_URL` y `MERCADOPAGO_BACK_URL_SUCCESS`/`_FAILURE`/`_PENDING` (HTTPS de la SPA).

Cuando Préstamos y Evidencias publiquen sus eventos: `LOAN_CREATED_ROUTING_KEY`, `LOAN_ACTIVATED_ROUTING_KEY`, `LOAN_FINISHED_ROUTING_KEY`, `EXTENSION_ACCEPTED_ROUTING_KEY`, `INCIDENT_RESOLVED_ROUTING_KEY`.

Opcionales: `SERVER_PORT=8082`, `DB_POOL_MAX_SIZE=20`, `OUTBOX_RELAY_DELAY_MS=1000`, `PENDING_RECONCILIATION_DELAY_MS=300000`, `PENDING_RECONCILIATION_MIN_AGE_MS=600000`, `MERCADOPAGO_CONNECT_TIMEOUT_MS`, `MERCADOPAGO_READ_TIMEOUT_MS`, `IDENTITY_*_TIMEOUT_MS`. No definir `MERCADOPAGO_API_BASE_URL` en producción (por defecto `https://api.mercadopago.com`). Mantener `SPRINGDOC_ENABLED=false` y `SWAGGER_PUBLIC=false`.

Secretos (token de Mercado Pago, secreto del webhook, credenciales de BD y broker, ADC Firebase) en Secrets Manager/Parameter Store; nunca en la imagen, Git ni logs.

## Base de datos

El informe pide una instancia MySQL separada para Pagos (datos económicos). Base `lendup_pagos`, InnoDB. Usuario de runtime limitado a ese esquema y, para que la auditoría sea solo de inserción (RNF21):

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON lendup_pagos.* TO 'lendup_pagos'@'%';
REVOKE UPDATE, DELETE ON lendup_pagos.registro_auditoria FROM 'lendup_pagos'@'%';
```

(En MySQL, revocar sobre una tabla requiere otorgar los permisos por tabla en vez de con `*`: otorgar `SELECT, INSERT` sobre `registro_auditoria` y `SELECT, INSERT, UPDATE, DELETE` sobre las demás.) Flyway debe correr con un usuario DDL en una única tarea antes de escalar.

## Red

- El webhook `POST /api/v1/webhooks/mercado-pago` necesita URL pública HTTPS en el API Gateway, sin autenticación de Firebase, y debe reenviar los headers `x-signature` y `x-request-id` y el query string `data.id`/`type` sin modificar.
- El servicio necesita salida HTTPS a `api.mercadopago.com`.
- Gateway: `/api/v1/medios-pago`, `/api/v1/pagos`, `/api/v1/garantias`, `/api/v1/prestamos/{id}/transacciones` (el resto de `/api/v1/prestamos/**` sigue en el monolito) y `/api/v1/webhooks/mercado-pago`. Propagar `Authorization` y `X-Correlation-ID`.
- MySQL y RabbitMQ en subredes privadas; RabbitMQ con TLS; la consola 15672 no se expone.

La imagen corre como UID/GID 10001 con filesystem de solo lectura y `/tmp` temporal (igual que Reservas).

## Despliegue y rollback

1. Aprovisionar BD, broker y secretos; registrar el webhook en el panel de Mercado Pago (producción) y copiar su secreto.
2. Ejecutar Flyway, desplegar una instancia, comprobar readiness y luego escalar. El relay y la conciliación son seguros con varias réplicas porque las operaciones son idempotentes, pero conviene iniciar con una.
3. Verificar un pago real de bajo monto, el webhook, el outbox y la DLQ.

Rollback: volver al digest anterior sin revertir Flyway. Antes de reintentar, revisar `outbox_events`, transacciones `PENDIENTE`/`FALLIDA` y la DLQ.
