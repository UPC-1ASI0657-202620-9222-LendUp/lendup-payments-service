# Pruebas

`mvnw.cmd clean verify` ejecuta:

- **Dominio:** bloques iniciados de 24 h y redondeo (R26), transiciones de `TransaccionEconomica`, invariantes del agregado `Garantia` (nunca afecta ni devuelve más que el saldo) y `LiquidacionDeGarantiaService` (sin afectación, parcial, total, montos incompatibles).
- **Aplicación:** `PaymentApplicationServiceTest` y `GuaranteeApplicationServiceTest` con persistencia en memoria y un adaptador falso de Mercado Pago (`support/FakePaymentGateway`, solo para pruebas): idempotencia por clave, monto y participación, confirmación y rechazo, notificaciones repetidas, liberación única, extensión, reembolsos idempotentes, timeout que conserva `PENDIENTE` (RNF14), conciliación, pago tardío tras cancelar, devolución total, retención parcial y total.
- **Seguridad:** token ausente, inválido, correo no verificado y rechazo de roles enviados por header (`FirebaseAuthenticationFilterTest`); firma del webhook (`MercadoPagoSignatureValidatorTest`).
- **Contrato con el frontend:** `FrontendContractTest` (MockMvc): rutas, cuerpos del frontend, 201/400/409, formato `{status, error, message}`, campos que lee `mappers/domain.ts` y `checkout_url`.
- **Mercado Pago simulado con WireMock:** `MercadoPagoAdapterWireMockTest`: preferencia (`external_reference`, `notification_url`, `auto_return`), consulta de pago y comisión, búsqueda por referencia, reembolso con `X-Idempotency-Key`, rechazo definitivo, 5xx y timeout sin reintentos ocultos. `IdentidadRestAdapterTest` cubre el contrato interno y el modo local contra el monolito.
- **Mensajería:** `EconomicEventConsumerTest` con los mensajes de ejemplo (incluidos los de productores pendientes), dedupe por inbox y errores que van a la DLQ; `OutboxRelayTest` (broker caído deja el evento pendiente).
- **Aceptación (Cucumber, `src/test/resources/features`, Gherkin en español):** US33 Realizar pago, US17 Constituir garantía, US34 Consultar estado de pagos y garantía, US36 Recibir devolución de garantía, US41 Recibir reembolso por cancelación. Reporte HTML: `target/cucumber-report.html`.
- **Integración `*IT` (failsafe, Testcontainers):**
  - `MySqlPaymentsIT`: Flyway + `ddl-auto=validate` en MySQL 8.4, idempotencia por clave vía API, webhook firmado procesado una sola vez con outbox en la misma transacción, firma inválida, dedupe del consumidor con reembolso tras el commit, dos solicitudes concurrentes con la misma clave, rollback conjunto negocio/outbox y reglas de seguridad.
  - `RabbitMessagingIT`: publicación con publisher confirms, consumo desde el broker con dedupe y mensaje inválido en la DLQ.

Las pruebas Testcontainers llevan `disabledWithoutDocker=true`: sin Docker se marcan omitidas. Una omisión no equivale a éxito. En la máquina donde se construyó el servicio Docker Desktop no estaba disponible y estas 10 pruebas quedaron omitidas; deben ejecutarse con Docker activo (también corren en GitHub Actions).

Prueba manual mínima:

```powershell
.\mvnw.cmd clean verify
docker compose up --build -d
Invoke-RestMethod http://localhost:8082/actuator/health/readiness
docker compose logs payments --tail 100
docker compose down
```

La colección `docs/postman/lendup-payments.postman_collection.json` cubre las cinco rutas, firma el webhook en un script previo con la variable `webhook_secret` y publica los eventos simulados en RabbitMQ (puerto de administración 15673).

Para RNF06 (95 % de cambios de estado reflejados en 5 s desde un webhook válido) debe medirse en un entorno equivalente a producción; no se incluyen resultados de carga.
