# Pruebas

`mvnw.cmd clean verify` ejecuta:

- **Dominio:** bloques iniciados de 24 h y redondeo (R26), transiciones de `TransaccionEconomica`, invariantes del agregado `Garantia` (nunca afecta ni devuelve más que el saldo) y `LiquidacionDeGarantiaService` (sin afectación, parcial, total, montos incompatibles).
- **Aplicación:** `PaymentApplicationServiceTest` y `GuaranteeApplicationServiceTest` con persistencia en memoria y un adaptador falso de Mercado Pago (`support/FakePaymentGateway`, solo para pruebas): idempotencia por clave, monto y participación, confirmación y rechazo, notificaciones repetidas, liberación única, extensión, reembolsos idempotentes, timeout que conserva `PENDIENTE` (RNF14), conciliación, pago tardío tras cancelar, devolución total, retención parcial y total.
- **Seguridad:** token ausente, inválido, correo no verificado y rechazo de roles enviados por header (`FirebaseAuthenticationFilterTest`); firma del webhook (`MercadoPagoSignatureValidatorTest`).
- **Contrato con el frontend:** `FrontendContractTest` (MockMvc): rutas, cuerpos del frontend, 201/400/409, formato `{status, error, message}`, campos que lee `mappers/domain.ts` y `checkout_url`.
- **Mercado Pago simulado con WireMock:** `MercadoPagoAdapterWireMockTest`: preferencia (`external_reference`, `notification_url`, `auto_return`), consulta de pago y comisión, búsqueda por referencia, reembolso con `X-Idempotency-Key`, rechazo definitivo, 5xx y timeout sin reintentos ocultos. `IdentidadRestAdapterTest` cubre el contrato interno y el modo local contra el monolito.
- **Mensajería:** `EconomicEventConsumerTest` con los mensajes de ejemplo (incluidos los de productores pendientes), dedupe por inbox y errores que van a la DLQ; `OutboxRelayTest`: el evento queda pendiente si el broker está caído, si ninguna cola lo recibe (`mandatory`) o si su tipo es desconocido, y se reintenta con espera creciente.
- **Aceptación (Cucumber, `src/test/resources/features`, Gherkin en español):** US33 Realizar pago, US17 Constituir garantía, US34 Consultar estado de pagos y garantía, US36 Recibir devolución de garantía, US41 Recibir reembolso por cancelación. Reporte HTML: `target/cucumber-report.html`.
- **Integración `*IT` (failsafe, Testcontainers):**
  - `MySqlPaymentsIT`: Flyway + `ddl-auto=validate` en MySQL 8.4, idempotencia por clave vía API, webhook firmado procesado una sola vez con outbox en la misma transacción, firma inválida, dedupe del consumidor con reembolso tras el commit, dos solicitudes concurrentes con la misma clave, rollback conjunto negocio/outbox y reglas de seguridad.
  - `RabbitMessagingIT`: publicación con publisher confirms, consumo desde el broker con dedupe y mensaje inválido en la DLQ.
  - `SimulatedMercadoPagoIT`: con `MERCADOPAGO_MODE=simulado`, garantía y tarifa se pagan en el simulador. Comprueba la transacción `CONFIRMADA`, el evento en el outbox, la redirección al frontend y el rechazo.

Las pruebas Testcontainers llevan `disabledWithoutDocker=true`: sin Docker se marcan omitidas, y una omisión no equivale a éxito. Con Docker activo pasan las 12 (también corren en GitHub Actions).

Prueba manual mínima:

```powershell
.\mvnw.cmd clean verify
docker compose up --build -d
Invoke-RestMethod http://localhost:8082/actuator/health/readiness
docker compose logs payments --tail 100
docker compose down
```

La colección `docs/postman/lendup-payments.postman_collection.json` sirve para probar sin llaves de Mercado Pago (`MERCADOPAGO_MODE=simulado`). Orden:

1. **0b. Preparación:**
   - token de Firebase (o pegar `firebase_token`);
   - `usuario.id` en Identidad;
   - cola espía `debug.lendup.eventos`.
2. **1. Eventos simulados:** `ReservaConfirmada` y `PrestamoCreado`. Cada uno debe responder `routed=true`.
3. **2. Pagos y garantías:** constituir la garantía o pagar la tarifa.
4. **3. Mercado Pago simulado:** aprobar el pago.
5. **0b. Ver eventos en la cola espía:** deben aparecer `pagos.garantia-constituida.v1` y `pagos.pago-confirmado.v1`.

La carpeta 4 firma el webhook con `webhook_secret`. Puerto de administración de RabbitMQ: 15675.

Esa secuencia se corrió con newman contra Pagos y Notificaciones sobre un solo broker: 17/17 verificaciones en Pagos y 13/13 en Notificaciones. Los avisos de Notificaciones se generaron a partir de los eventos publicados por Pagos.

Para RNF06 (95 % de cambios de estado reflejados en 5 s desde un webhook válido) debe medirse en un entorno equivalente a producción; no se incluyen resultados de carga.
