package com.lendup.pagos.infrastructure.persistence.repository;
import com.lendup.pagos.infrastructure.persistence.entity.GarantiaEntity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface GarantiaJpaRepository extends JpaRepository<GarantiaEntity,UUID>{
  @Query("select g from GarantiaEntity g where g.prestamoId=:prestamoId") Optional<GarantiaEntity> findByPrestamoId(@Param("prestamoId") UUID prestamoId);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select g from GarantiaEntity g where g.id=:id") Optional<GarantiaEntity> lockById(@Param("id") UUID id);
}
