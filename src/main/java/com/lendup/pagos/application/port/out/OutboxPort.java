package com.lendup.pagos.application.port.out;
import com.lendup.pagos.domain.event.EventEnvelope;
public interface OutboxPort { void append(EventEnvelope event); }
