package com.lendup.pagos.application.port.in;

import com.lendup.pagos.application.port.in.PaymentViews.*;
import com.lendup.pagos.application.port.out.PaymentGatewayPort.ProviderPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pagos de tarifa y extensión, consultas, liberaciones, reembolsos y resultados del proveedor. */
public interface PaymentUseCases {
  List<MedioPagoView> listarMediosPago();
  TransaccionView crearPago(UUID actor, CrearPagoCommand command, String correlationId);
  List<TransaccionView> listarTransacciones(UUID actor, UUID prestamoId);
  /** Costo del préstamo concepto por concepto, sin generar ninguna transacción (RF37, US32). */
  DesglosePagoView desglosarPago(UUID actor, UUID prestamoId);

  /** Aplica el estado consultado al proveedor. Devuelve los reembolsos que deben ejecutarse tras el commit. */
  List<UUID> aplicarResultadoCobro(ProviderPayment payment, String correlationId);
  void ejecutarReembolso(UUID transaccionId, String correlationId);
  int conciliarPendientes(Instant solicitadasAntesDe, int limit);

  void registrarReservaConfirmada(ReservaConfirmadaCommand command, String correlationId);
  void vincularPrestamo(UUID reservaId, UUID prestamoId, String correlationId);
  void registrarPrestamoActivado(UUID prestamoId, String correlationId);
  void registrarExtensionAceptada(UUID prestamoId, UUID cambioFechaPrestamoId, BigDecimal costoAdicional, String correlationId);
  void registrarExtensionConRetencion(UUID prestamoId, UUID cambio, BigDecimal costo, Instant expiraEn, String correlationId);
  List<UUID> registrarRetencionRechazada(UUID prestamoId, UUID cambio, String correlationId);
  void registrarExtensionAplicada(UUID prestamoId, UUID cambio, String correlationId);
  /** El objeto ya se devolvió: una extensión sin pagar deja de corresponder. */
  void registrarDevolucion(UUID prestamoId, String correlationId);
  List<UUID> registrarReservaCancelada(UUID reservaId, String correlationId);
}
