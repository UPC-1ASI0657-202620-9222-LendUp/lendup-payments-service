# Contrato API

Base externa: `/api/v1`. Todos los endpoints, salvo el webhook, requieren `Authorization: Bearer <Firebase ID token>` y usan JSON `snake_case`. Contrato formal: [`contracts/openapi/payments-v1.yaml`](../contracts/openapi/payments-v1.yaml).

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/medios-pago` | 200, `[{"codigo":"MERCADO_PAGO","nombre":"Mercado Pago","estado":"ACTIVO"}]` |
| POST | `/pagos` | 201, transacción `PENDIENTE` + `checkout_url`. Solo el prestatario |
| POST | `/garantias` | 201, cobro `CONSTITUCION_GARANTIA` `PENDIENTE` + `garantia` `PENDIENTE` + `checkout_url`. Solo el prestatario |
| GET | `/prestamos/{id}/transacciones` | 200, solo participantes del préstamo |
| POST | `/webhooks/mercado-pago` | 201 `{proveedor_evento_id, estado}`; público, autenticado por firma |

Errores: `{ "status": 409, "error": "CONFLICTO_NEGOCIO", "message": "...", "timestamp": "...Z" }`. Códigos: 400 `SOLICITUD_INVALIDA`, 401 `AUTENTICACION_REQUERIDA`/`TOKEN_INVALIDO`/`FIRMA_INVALIDA`, 403 `NO_AUTORIZADO`/`EMAIL_NOT_VERIFIED`, 404 `NO_ENCONTRADO`, 409 `CONFLICTO_NEGOCIO`/`CONFLICTO_DATOS`, 503 `DEPENDENCIA_NO_DISPONIBLE`.

## Cuerpos (iguales al monolito)

```json
POST /pagos
{"prestamo_id":"UUID","cambio_fecha_prestamo_id":null,"tipo":"PAGO_TARIFA","monto":60.00,"medio_pago_seleccionado":"MERCADO_PAGO","clave_idempotencia":"pago-UUID"}

POST /garantias
{"prestamo_id":"UUID","medio_pago_seleccionado":"MERCADO_PAGO","clave_idempotencia":"garantia-UUID"}
```

## Reglas

- `PAGO_TARIFA`: el préstamo debe estar `RESERVADO`, sin tarifa ya pagada, y `monto` = tarifa diaria × bloques iniciados de 24 h entre `desde` y `hasta` de la reserva (R26, mínimo un bloque, redondeo a 2 decimales). Si no coincide: 400 `El importe no coincide con la tarifa acordada`.
- `PAGO_EXTENSION`: préstamo `ACTIVO`, con una extensión aceptada (evento de Préstamos); `cambio_fecha_prestamo_id` y `monto` deben coincidir con ella.
- `medio_pago_seleccionado` solo admite `MERCADO_PAGO` (en garantías es opcional y por defecto es ese).
- Idempotencia: la misma `clave_idempotencia` devuelve la misma transacción (y su `checkout_url`); usada en otro préstamo o tipo → 409. Si ya hay un cobro `PENDIENTE` del mismo tipo, se devuelve ese en vez de crear otro.
- `POST /garantias` → 409 `El préstamo no requiere garantía` o `Garantía ya registrada` (si ya no está `PENDIENTE`). Si el intento anterior fue rechazado, se crea un nuevo cobro para la misma garantía.
- Si Mercado Pago no responde al crear la preferencia: 503 y la transacción queda `PENDIENTE` sin URL; reintentar con la misma clave.
- El rol de acceso no basta: la participación se valida contra la proyección `prestamos_pagables`.

## Cambios respecto al monolito (para el frontend)

- Nuevo campo `checkout_url`: redirigir al usuario a Checkout Pro. Las `back_urls` (`MERCADOPAGO_BACK_URL_*`) vuelven a la SPA, que debe consultar `GET /prestamos/{id}/transacciones` hasta ver el estado final.
- `GET /medios-pago` devuelve `MERCADO_PAGO` en vez de `PAGO_EN_LINEA`.
- Estados de transacción (Fig. 11): `PENDIENTE`, `CONFIRMADA`, `RECHAZADA`, `CANCELADA`, `FALLIDA`. Ya no se usan `PENDIENTE_LIBERACION` ni `CONSTITUIDA` como estado de transacción; la liberación es una transacción `LIBERACION_TARIFA`. Estados de garantía: `PENDIENTE`, `CONSTITUIDA`, `RETENIDA_POR_INCIDENCIA`, `DEVUELTA`, `AFECTADA_PARCIALMENTE`, `AFECTADA_TOTALMENTE`, `CANCELADA`.
- Timestamps `_en` como instantes UTC (`...Z`), igual que Reservas.

## Webhook

Mercado Pago envía `POST /webhooks/mercado-pago?data.id=<pago>&type=payment` con headers `x-signature: ts=<ts>,v1=<hmac>` y `x-request-id`. El servicio valida HMAC-SHA256 de `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` con `MERCADOPAGO_WEBHOOK_SECRET`, registra la notificación (`notificaciones_proveedor`), descarta las ya procesadas, consulta `GET /v1/payments/{id}` y recién entonces cambia estados. Respuestas: `PROCESADA`, `IGNORADA` (otro tipo o pago desconocido) o `DUPLICADA`; 401 si la firma no es válida; 503 si Mercado Pago no responde (el proveedor reintenta).

## Contrato interno pendiente con Identidad

`IDENTITY_FIREBASE_USER_URI_TEMPLATE` con el UID como única variable. Con `IDENTITY_SERVICE_TOKEN` se espera `{ "usuario_id": "UUID" }`. Sin token se reenvía el ID token del usuario, lo que permite usar en local `GET /api/v1/estudiantes/me` del monolito (`{ "usuario": { "id": "UUID" } }`).
