package com.lendup.pagos.infrastructure.persistence.repository;
import com.lendup.pagos.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity,UUID>{
  @Query("select e from OutboxEventEntity e where e.publishedAt is null and e.attempts<:maxAttempts and (e.nextAttemptAt is null or e.nextAttemptAt<=:now) order by e.occurredAt")
  List<OutboxEventEntity> pending(@org.springframework.data.repository.query.Param("now") Instant now,@org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts,Pageable page);
}
