package com.lendup.pagos.application.port.out;

import com.lendup.pagos.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Transaction Repository (Fig. 18): persistencia de transacciones, garantías y la proyección prestamos_pagables. */
public interface TransactionRepository {
  TransaccionEconomica saveTransaccion(TransaccionEconomica transaccion);
  Optional<TransaccionEconomica> findTransaccion(UUID id);
  Optional<TransaccionEconomica> lockTransaccion(UUID id);
  Optional<TransaccionEconomica> findByClaveIdempotencia(String clave);
  List<TransaccionEconomica> findByPrestamo(UUID prestamoId);
  List<TransaccionEconomica> findPendientes(Collection<TipoTransaccion> tipos, Instant solicitadasAntesDe, int limit);
  BigDecimal sumMontoPendiente(UUID garantiaId, TipoTransaccion tipo);

  Garantia saveGarantia(Garantia garantia);
  Optional<Garantia> findGarantiaByPrestamo(UUID prestamoId);
  Optional<Garantia> lockGarantia(UUID id);

  PrestamoPagable saveProyeccion(PrestamoPagable proyeccion);
  Optional<PrestamoPagable> lockProyeccionByReserva(UUID reservaId);
  Optional<PrestamoPagable> findProyeccionByPrestamo(UUID prestamoId);
  Optional<PrestamoPagable> lockProyeccionByPrestamo(UUID prestamoId);
}
