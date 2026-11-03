package com.lendup.pagos.support;

import com.lendup.pagos.application.port.out.TransactionRepository;
import com.lendup.pagos.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Repositorio en memoria para pruebas de aplicación y escenarios Cucumber (sin base de datos). */
public class InMemoryTransactionRepository implements TransactionRepository {
  public final Map<UUID, TransaccionEconomica> transacciones = new LinkedHashMap<>();
  public final Map<UUID, Garantia> garantias = new LinkedHashMap<>();
  public final Map<UUID, PrestamoPagable> proyecciones = new LinkedHashMap<>();

  public TransaccionEconomica saveTransaccion(TransaccionEconomica t) {
    boolean duplicatedKey = transacciones.values().stream().anyMatch(o -> !o.id().equals(t.id()) && o.claveIdempotencia().equals(t.claveIdempotencia()));
    if (duplicatedKey) throw new org.springframework.dao.DataIntegrityViolationException("clave_idempotencia duplicada");
    transacciones.put(t.id(), t); return t;
  }
  public Optional<TransaccionEconomica> findTransaccion(UUID id) { return Optional.ofNullable(transacciones.get(id)); }
  public Optional<TransaccionEconomica> lockTransaccion(UUID id) { return findTransaccion(id); }
  public Optional<TransaccionEconomica> findByClaveIdempotencia(String clave) { return transacciones.values().stream().filter(t -> t.claveIdempotencia().equals(clave)).findFirst(); }
  public List<TransaccionEconomica> findByPrestamo(UUID prestamoId) { return transacciones.values().stream().filter(t -> t.prestamoId().equals(prestamoId)).toList(); }
  public List<TransaccionEconomica> findPendientes(Collection<TipoTransaccion> tipos, Instant antes, int limit) {
    return transacciones.values().stream().filter(t -> t.estado() == EstadoTransaccion.PENDIENTE && tipos.contains(t.tipo()) && t.solicitadaEn().isBefore(antes)).limit(limit).toList();
  }
  public BigDecimal sumMontoPendiente(UUID garantiaId, TipoTransaccion tipo) {
    return transacciones.values().stream().filter(t -> garantiaId.equals(t.garantiaId()) && t.tipo() == tipo && t.estado() == EstadoTransaccion.PENDIENTE)
        .map(TransaccionEconomica::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
  public Garantia saveGarantia(Garantia g) { garantias.put(g.id(), g); return g; }
  public Optional<Garantia> findGarantiaByPrestamo(UUID prestamoId) { return garantias.values().stream().filter(g -> g.prestamoId().equals(prestamoId)).findFirst(); }
  public Optional<Garantia> lockGarantia(UUID id) { return Optional.ofNullable(garantias.get(id)); }
  public PrestamoPagable saveProyeccion(PrestamoPagable p) { proyecciones.put(p.reservaId(), p); return p; }
  public Optional<PrestamoPagable> lockProyeccionByReserva(UUID reservaId) { return Optional.ofNullable(proyecciones.get(reservaId)); }
  public Optional<PrestamoPagable> findProyeccionByPrestamo(UUID prestamoId) { return proyecciones.values().stream().filter(p -> prestamoId.equals(p.prestamoId())).findFirst(); }
  public Optional<PrestamoPagable> lockProyeccionByPrestamo(UUID prestamoId) { return findProyeccionByPrestamo(prestamoId); }

  public List<TransaccionEconomica> ofType(TipoTransaccion tipo) { return transacciones.values().stream().filter(t -> t.tipo() == tipo).toList(); }
}
