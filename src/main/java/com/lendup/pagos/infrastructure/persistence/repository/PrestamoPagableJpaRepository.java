package com.lendup.pagos.infrastructure.persistence.repository;
import com.lendup.pagos.infrastructure.persistence.entity.PrestamoPagableEntity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface PrestamoPagableJpaRepository extends JpaRepository<PrestamoPagableEntity,UUID>{
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PrestamoPagableEntity p where p.reservaId=:reservaId") Optional<PrestamoPagableEntity> lockByReservaId(@Param("reservaId") UUID reservaId);
  @Query("select p from PrestamoPagableEntity p where p.prestamoId=:prestamoId") Optional<PrestamoPagableEntity> findByPrestamoId(@Param("prestamoId") UUID prestamoId);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PrestamoPagableEntity p where p.prestamoId=:prestamoId") Optional<PrestamoPagableEntity> lockByPrestamoId(@Param("prestamoId") UUID prestamoId);
}
