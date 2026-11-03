package com.lendup.pagos.domain.service;

import com.lendup.pagos.domain.model.DecisionGarantia;
import com.lendup.pagos.domain.model.Garantia;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain Service (informe, patrón 22): calcula cuánto retener y cuánto devolver según la resolución de una
 * incidencia. Mercado Pago solo ejecuta el reembolso del saldo; la decisión de montos pertenece a LendUp.
 */
public class LiquidacionDeGarantiaService {

  public record Liquidacion(BigDecimal retencion, BigDecimal devolucion) {}

  /**
   * Las reglas siguen la validación del monolito (IncidentsService.resolve). Como el evento llega después de la
   * resolución, si el monto informado supera el saldo disponible se limita al saldo en lugar de fallar.
   */
  public Liquidacion liquidar(Garantia garantia, DecisionGarantia decision, BigDecimal montoAfectado) {
    if (decision == null) throw new IllegalArgumentException("Se requiere la decisión sobre la garantía");
    BigDecimal saldo = garantia.saldoDisponible();
    BigDecimal monto = montoAfectado == null ? BigDecimal.ZERO : montoAfectado.setScale(2, RoundingMode.HALF_UP);
    if (monto.signum() < 0) throw new IllegalArgumentException("El monto afectado no puede ser negativo");
    BigDecimal retencion = switch (decision) {
      case SIN_AFECTACION -> {
        if (monto.signum() != 0) throw new IllegalArgumentException("Sin afectación no admite monto afectado");
        yield BigDecimal.ZERO.setScale(2);
      }
      case AFECTACION_PARCIAL -> {
        if (monto.signum() == 0) throw new IllegalArgumentException("La afectación parcial requiere un monto positivo");
        yield monto.min(saldo);
      }
      case AFECTACION_TOTAL -> saldo;
    };
    return new Liquidacion(retencion, saldo.subtract(retencion));
  }
}
