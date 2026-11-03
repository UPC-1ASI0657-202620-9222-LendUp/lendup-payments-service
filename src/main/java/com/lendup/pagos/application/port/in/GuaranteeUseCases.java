package com.lendup.pagos.application.port.in;

import com.lendup.pagos.application.port.in.PaymentViews.*;
import java.util.List;
import java.util.UUID;

/** Constitución, devolución y retención de la garantía monetaria. */
public interface GuaranteeUseCases {
  TransaccionView constituirGarantia(UUID actor, ConstituirGarantiaCommand command, String correlationId);
  /** Devuelven los reembolsos PENDIENTE creados, que se ejecutan después del commit. */
  List<UUID> registrarPrestamoFinalizado(UUID prestamoId, String correlationId);
  List<UUID> registrarIncidenciaResuelta(IncidenciaResueltaCommand command, String correlationId);
}
