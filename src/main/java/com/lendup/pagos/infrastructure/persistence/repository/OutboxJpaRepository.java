package com.lendup.pagos.infrastructure.persistence.repository;
import com.lendup.pagos.infrastructure.persistence.entity.OutboxEventEntity;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity,UUID>{
  @Query("select e from OutboxEventEntity e where e.publishedAt is null and e.attempts<10 order by e.occurredAt") List<OutboxEventEntity> pending(Pageable page);
}
