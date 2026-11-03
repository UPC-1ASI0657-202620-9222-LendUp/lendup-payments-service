package com.lendup.pagos.infrastructure.persistence.repository;
import com.lendup.pagos.infrastructure.persistence.entity.NotificacionProveedorEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface NotificacionProveedorJpaRepository extends JpaRepository<NotificacionProveedorEntity,UUID>{
  @Query("select n from NotificacionProveedorEntity n where n.proveedorEventoId=:eventoId") Optional<NotificacionProveedorEntity> findByProveedorEventoId(@Param("eventoId") String eventoId);
}
