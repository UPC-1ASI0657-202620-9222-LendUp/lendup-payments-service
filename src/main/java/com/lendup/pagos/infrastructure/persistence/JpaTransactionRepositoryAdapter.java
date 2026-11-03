package com.lendup.pagos.infrastructure.persistence;

import com.lendup.pagos.application.port.out.TransactionRepository;
import com.lendup.pagos.domain.model.*;
import com.lendup.pagos.infrastructure.persistence.entity.*;
import com.lendup.pagos.infrastructure.persistence.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class JpaTransactionRepositoryAdapter implements TransactionRepository {
  private final TransaccionEconomicaJpaRepository transacciones; private final GarantiaJpaRepository garantias; private final PrestamoPagableJpaRepository proyecciones;
  public JpaTransactionRepositoryAdapter(TransaccionEconomicaJpaRepository t,GarantiaJpaRepository g,PrestamoPagableJpaRepository p){transacciones=t;garantias=g;proyecciones=p;}

  public TransaccionEconomica saveTransaccion(TransaccionEconomica t){TransaccionEconomicaEntity e=transacciones.findById(t.id()).map(x->{x.copy(t);return x;}).orElseGet(()->TransaccionEconomicaEntity.from(t));return transacciones.saveAndFlush(e).toDomain();}
  public Optional<TransaccionEconomica> findTransaccion(UUID id){return transacciones.findById(id).map(TransaccionEconomicaEntity::toDomain);}
  public Optional<TransaccionEconomica> lockTransaccion(UUID id){return transacciones.lockById(id).map(TransaccionEconomicaEntity::toDomain);}
  public Optional<TransaccionEconomica> findByClaveIdempotencia(String clave){return transacciones.findByClave(clave).map(TransaccionEconomicaEntity::toDomain);}
  public List<TransaccionEconomica> findByPrestamo(UUID prestamoId){return transacciones.findByPrestamo(prestamoId).stream().map(TransaccionEconomicaEntity::toDomain).toList();}
  public List<TransaccionEconomica> findPendientes(Collection<TipoTransaccion> tipos,Instant antes,int limit){return transacciones.pendientes(tipos,antes,PageRequest.of(0,limit)).stream().map(TransaccionEconomicaEntity::toDomain).toList();}
  public BigDecimal sumMontoPendiente(UUID garantiaId,TipoTransaccion tipo){return transacciones.sumPendiente(garantiaId,tipo);}

  public Garantia saveGarantia(Garantia g){GarantiaEntity e=garantias.findById(g.id()).map(x->{x.copy(g);return x;}).orElseGet(()->GarantiaEntity.from(g));return garantias.saveAndFlush(e).toDomain();}
  public Optional<Garantia> findGarantiaByPrestamo(UUID prestamoId){return garantias.findByPrestamoId(prestamoId).map(GarantiaEntity::toDomain);}
  public Optional<Garantia> lockGarantia(UUID id){return garantias.lockById(id).map(GarantiaEntity::toDomain);}

  public PrestamoPagable saveProyeccion(PrestamoPagable p){PrestamoPagableEntity e=proyecciones.findById(p.reservaId()).map(x->{x.copy(p);return x;}).orElseGet(()->PrestamoPagableEntity.from(p));return proyecciones.saveAndFlush(e).toDomain();}
  public Optional<PrestamoPagable> lockProyeccionByReserva(UUID reservaId){return proyecciones.lockByReservaId(reservaId).map(PrestamoPagableEntity::toDomain);}
  public Optional<PrestamoPagable> findProyeccionByPrestamo(UUID prestamoId){return proyecciones.findByPrestamoId(prestamoId).map(PrestamoPagableEntity::toDomain);}
  public Optional<PrestamoPagable> lockProyeccionByPrestamo(UUID prestamoId){return proyecciones.lockByPrestamoId(prestamoId).map(PrestamoPagableEntity::toDomain);}
}
