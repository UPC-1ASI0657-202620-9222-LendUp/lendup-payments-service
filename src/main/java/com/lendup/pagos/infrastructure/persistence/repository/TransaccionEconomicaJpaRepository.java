package com.lendup.pagos.infrastructure.persistence.repository;
import com.lendup.pagos.domain.model.TipoTransaccion;
import com.lendup.pagos.infrastructure.persistence.entity.TransaccionEconomicaEntity;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface TransaccionEconomicaJpaRepository extends JpaRepository<TransaccionEconomicaEntity,UUID>{
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TransaccionEconomicaEntity t where t.id=:id") Optional<TransaccionEconomicaEntity> lockById(@Param("id") UUID id);
  @Query("select t from TransaccionEconomicaEntity t where t.claveIdempotencia=:clave") Optional<TransaccionEconomicaEntity> findByClave(@Param("clave") String clave);
  @Query("select t from TransaccionEconomicaEntity t where t.prestamoId=:prestamoId order by t.solicitadaEn, t.id") List<TransaccionEconomicaEntity> findByPrestamo(@Param("prestamoId") UUID prestamoId);
  @Query("select t from TransaccionEconomicaEntity t where t.estado=com.lendup.pagos.domain.model.EstadoTransaccion.PENDIENTE and t.tipo in :tipos and t.solicitadaEn<:antes order by t.solicitadaEn")
  List<TransaccionEconomicaEntity> pendientes(@Param("tipos") Collection<TipoTransaccion> tipos,@Param("antes") Instant antes,Pageable page);
  @Query("select coalesce(sum(t.monto),0) from TransaccionEconomicaEntity t where t.garantiaId=:garantiaId and t.tipo=:tipo and t.estado=com.lendup.pagos.domain.model.EstadoTransaccion.PENDIENTE")
  BigDecimal sumPendiente(@Param("garantiaId") UUID garantiaId,@Param("tipo") TipoTransaccion tipo);
}
