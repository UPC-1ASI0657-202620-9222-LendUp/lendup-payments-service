package com.lendup.pagos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Strategy (informe, patrón 25): comisión que recibe LendUp por cada préstamo. Se calcula sobre la tarifa, es
 * independiente de la garantía y se descuenta de lo que recibe el prestamista (ADR 0003).
 */
public interface PoliticaDeComision {
  BigDecimal comision(BigDecimal tarifa);

  static PoliticaDeComision porcentaje(BigDecimal porcentaje) {
    if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(BigDecimal.valueOf(100)) >= 0) {
      throw new IllegalArgumentException("El porcentaje de comisión debe estar entre 0 y 100 (sin incluir 100)");
    }
    return tarifa -> tarifa.multiply(porcentaje).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }
}
