package com.lendup.pagos.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(UUID eventId, String eventType, int eventVersion, long aggregateVersion,
                            UUID aggregateId, Instant occurredAt, String correlationId, Map<String,Object> payload) {}
