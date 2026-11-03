package com.lendup.pagos.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** Periodo acordado en la reserva (desde/hasta en hora de negocio America/Lima, sin offset). */
public record PeriodoPrestamo(LocalDateTime desde, LocalDateTime hasta) {
  public PeriodoPrestamo {
    if (desde == null || hasta == null || !desde.isBefore(hasta)) {
      throw new IllegalArgumentException("El periodo requiere desde < hasta");
    }
  }

  /** R26: se cobra cada bloque de 24 horas iniciado; un periodo menor a 24 h cuenta como un bloque. */
  public long bloquesIniciadosDe24Horas() {
    long seconds = Duration.between(desde, hasta).getSeconds();
    return Math.max(1, Math.addExact(seconds, 86_399L) / 86_400L);
  }
}
