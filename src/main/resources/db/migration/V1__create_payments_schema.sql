-- Esquema propio de Pagos y Garantías (lendup_pagos). Sin claves foráneas hacia tablas de otros servicios:
-- prestamo_id, reserva_id, usuarios, cambios de fecha e incidencias son referencias externas por identificador.

CREATE TABLE garantias (
  id BINARY(16) NOT NULL,
  prestamo_id BINARY(16) NOT NULL,
  monto_acordado DECIMAL(12,2) NOT NULL,
  moneda CHAR(3) NOT NULL,
  estado VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE',
  monto_constituido DECIMAL(12,2) NULL,
  monto_afectado DECIMAL(12,2) NOT NULL DEFAULT 0,
  monto_devuelto DECIMAL(12,2) NOT NULL DEFAULT 0,
  constituida_en DATETIME(6) NULL,
  cerrada_en DATETIME(6) NULL,
  creada_en DATETIME(6) NOT NULL,
  actualizada_en DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_garantias_prestamo_id (prestamo_id),
  INDEX idx_garantias_0 (estado, actualizada_en),
  CONSTRAINT ck_garantias_estado CHECK (estado IN ('PENDIENTE','CONSTITUIDA','RETENIDA_POR_INCIDENCIA','DEVUELTA','AFECTADA_PARCIALMENTE','AFECTADA_TOTALMENTE','CANCELADA')),
  CONSTRAINT ck_garantias_montos CHECK (monto_acordado > 0 AND monto_afectado >= 0 AND monto_devuelto >= 0
    AND (monto_constituido IS NULL OR monto_afectado + monto_devuelto <= monto_constituido))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE transacciones_economicas (
  id BINARY(16) NOT NULL,
  prestamo_id BINARY(16) NOT NULL,
  garantia_id BINARY(16) NULL,
  cambio_fecha_prestamo_id BINARY(16) NULL,
  incidencia_id BINARY(16) NULL,
  tipo VARCHAR(32) NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  monto DECIMAL(12,2) NOT NULL,
  moneda CHAR(3) NOT NULL,
  comision_proveedor DECIMAL(12,2) NULL,
  medio_pago_seleccionado VARCHAR(100) NULL,
  referencia_proveedor VARCHAR(150) NULL,
  clave_idempotencia VARCHAR(150) NOT NULL,
  checkout_url VARCHAR(500) NULL,
  solicitada_en DATETIME(6) NOT NULL,
  confirmada_en DATETIME(6) NULL,
  actualizada_en DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_transacciones_economicas_clave_idempotencia (clave_idempotencia),
  INDEX idx_transacciones_economicas_0 (prestamo_id, solicitada_en),
  INDEX idx_transacciones_economicas_1 (garantia_id, solicitada_en),
  INDEX idx_transacciones_economicas_2 (referencia_proveedor),
  INDEX idx_transacciones_economicas_3 (estado, tipo, solicitada_en),
  CONSTRAINT fk_transacciones_economicas_garantia_id FOREIGN KEY (garantia_id) REFERENCES garantias (id) ON DELETE RESTRICT,
  CONSTRAINT ck_transacciones_tipo CHECK (tipo IN ('PAGO_TARIFA','PAGO_EXTENSION','CONSTITUCION_GARANTIA','LIBERACION_TARIFA','DEVOLUCION_GARANTIA','RETENCION_GARANTIA','REEMBOLSO_TARIFA')),
  CONSTRAINT ck_transacciones_estado CHECK (estado IN ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELADA','FALLIDA')),
  CONSTRAINT ck_transacciones_monto CHECK (monto > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Proyección local alimentada por eventos de Reservas y Préstamos. prestamo_id llega después de reserva_id.
CREATE TABLE prestamos_pagables (
  reserva_id BINARY(16) NOT NULL,
  prestamo_id BINARY(16) NULL,
  prestamista_usuario_id BINARY(16) NOT NULL,
  prestatario_usuario_id BINARY(16) NOT NULL,
  desde DATETIME(6) NOT NULL,
  hasta DATETIME(6) NOT NULL,
  tarifa_diaria_acordada DECIMAL(12,2) NOT NULL,
  garantia_monetaria_acordada DECIMAL(12,2) NULL,
  moneda CHAR(3) NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'RESERVADO',
  costo_extension_pendiente DECIMAL(12,2) NULL,
  cambio_fecha_pendiente_id BINARY(16) NULL,
  creado_en DATETIME(6) NOT NULL,
  actualizado_en DATETIME(6) NOT NULL,
  PRIMARY KEY (reserva_id),
  UNIQUE KEY uq_prestamos_pagables_prestamo_id (prestamo_id),
  INDEX idx_prestamos_pagables_prestatario (prestatario_usuario_id, estado),
  INDEX idx_prestamos_pagables_prestamista (prestamista_usuario_id, estado),
  CONSTRAINT ck_prestamos_pagables_estado CHECK (estado IN ('RESERVADO','ACTIVO','FINALIZADO','CANCELADO')),
  CONSTRAINT ck_prestamos_pagables_periodo CHECK (desde < hasta),
  CONSTRAINT ck_prestamos_pagables_tarifa CHECK (tarifa_diaria_acordada > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Notificaciones recibidas del proveedor (webhook). La unicidad de proveedor_evento_id descarta duplicados.
CREATE TABLE notificaciones_proveedor (
  id BINARY(16) NOT NULL,
  proveedor_evento_id VARCHAR(150) NOT NULL,
  tipo VARCHAR(50) NOT NULL,
  payload_json JSON NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'RECIBIDA',
  recibido_en DATETIME(6) NOT NULL,
  procesado_en DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_notificaciones_proveedor_evento (proveedor_evento_id),
  INDEX idx_notificaciones_proveedor_estado (estado, recibido_en),
  CONSTRAINT ck_notificaciones_proveedor_estado CHECK (estado IN ('RECIBIDA','PROCESADA','IGNORADA','ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tablas técnicas con los mismos nombres y columnas que lendup-reservations-service.
CREATE TABLE outbox_events (
  id BINARY(16) NOT NULL,
  aggregate_type VARCHAR(50) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  event_version INT NOT NULL,
  aggregate_version BIGINT NOT NULL,
  payload JSON NOT NULL,
  correlation_id VARCHAR(100) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  published_at DATETIME(6) NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  PRIMARY KEY (id),
  INDEX idx_outbox_pending (published_at, attempts, occurred_at),
  INDEX idx_outbox_aggregate (aggregate_id, aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processed_events (
  event_id BINARY(16) NOT NULL,
  consumer VARCHAR(100) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  processed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (event_id),
  INDEX idx_processed_aggregate (aggregate_id, aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Solo inserción (RNF21): la entidad JPA es inmutable y el usuario de producción solo recibe INSERT y SELECT
-- sobre esta tabla (ver docs/aws-handoff.md).
CREATE TABLE registro_auditoria (
  id BINARY(16) NOT NULL,
  aggregate_type VARCHAR(50) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  action VARCHAR(100) NOT NULL,
  previous_state VARCHAR(50) NULL,
  new_state VARCHAR(50) NOT NULL,
  actor_id BINARY(16) NULL,
  correlation_id VARCHAR(100) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_audit_aggregate (aggregate_type, aggregate_id, occurred_at),
  INDEX idx_audit_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
