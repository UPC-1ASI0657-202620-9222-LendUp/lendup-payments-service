package com.lendup.pagos.application.port.out;
import java.util.UUID;
public interface AuditPort { void record(String aggregateType, UUID aggregateId, String action, String previousState, String newState, UUID actorId, String correlationId); }
