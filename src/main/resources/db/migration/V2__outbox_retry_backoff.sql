-- Reintentos espaciados del Outbox: un evento que el broker no pudo entregar (broker caído o sin cola suscrita)
-- se vuelve a intentar desde next_attempt_at, con espera creciente, en vez de agotar los intentos en segundos.
ALTER TABLE outbox_events ADD COLUMN next_attempt_at DATETIME(6) NULL AFTER last_error;
