package com.lendup.pagos.infrastructure.persistence.repository;
import com.lendup.pagos.infrastructure.persistence.entity.AuditEntity; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditJpaRepository extends JpaRepository<AuditEntity,UUID>{}
