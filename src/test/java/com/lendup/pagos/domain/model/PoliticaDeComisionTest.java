package com.lendup.pagos.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Strategy (informe, patrón 25): comisión de LendUp por préstamo, independiente de la garantía. */
class PoliticaDeComisionTest {
  @ParameterizedTest @CsvSource({"0,60.00,0.00", "10,60.00,6.00", "8.5,40.00,3.40", "3,0.10,0.00", "15,33.33,5.00"})
  void calculaElPorcentajeDeLaTarifaConDosDecimales(BigDecimal porcentaje, BigDecimal tarifa, BigDecimal esperada) {
    assertEquals(esperada, PoliticaDeComision.porcentaje(porcentaje).comision(tarifa));
  }

  @ParameterizedTest @ValueSource(strings = {"-1", "100", "250"})
  void rechazaPorcentajesFueraDeRango(String porcentaje) {
    assertThrows(IllegalArgumentException.class, () -> PoliticaDeComision.porcentaje(new BigDecimal(porcentaje)));
  }

  @Test void rechazaPorcentajeNulo() { assertThrows(IllegalArgumentException.class, () -> PoliticaDeComision.porcentaje(null)); }
}
