package com.lendup.pagos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Strategy (informe, patrón 25): regla intercambiable para calcular el costo del préstamo. */
public interface PoliticaDeCosto {
  BigDecimal importe(PeriodoPrestamo periodo, BigDecimal tarifaDiaria);

  /** Regla vigente del monolito: tarifa diaria × bloques iniciados de 24 h, redondeo a 2 decimales. */
  PoliticaDeCosto POR_BLOQUES_DE_24_HORAS = (periodo, tarifaDiaria) -> {
    if (tarifaDiaria == null || tarifaDiaria.signum() <= 0) {
      throw new IllegalArgumentException("La tarifa diaria debe ser positiva");
    }
    return tarifaDiaria.multiply(BigDecimal.valueOf(periodo.bloquesIniciadosDe24Horas())).setScale(2, RoundingMode.HALF_UP);
  };
}
