-- Comisión de LendUp registrada en la liberación de la tarifa (RF37, PoliticaDeComision).
ALTER TABLE transacciones_economicas ADD COLUMN comision_lendup DECIMAL(12,2) NULL AFTER comision_proveedor;

-- Incidencias reportadas y aún no resueltas: mientras haya alguna, la garantía no se devuelve (RF41).
ALTER TABLE prestamos_pagables ADD COLUMN incidencias_pendientes INT NOT NULL DEFAULT 0 AFTER cambio_fecha_pendiente_id;
ALTER TABLE prestamos_pagables ADD CONSTRAINT ck_prestamos_pagables_incidencias CHECK (incidencias_pendientes >= 0);
