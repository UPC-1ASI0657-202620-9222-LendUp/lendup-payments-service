package com.lendup.pagos.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface ExtensionPaymentWindows {
  boolean open(UUID loanId, UUID changeId, Instant expiresAt);
  void reject(UUID loanId, UUID changeId);
  boolean active(UUID loanId, UUID changeId, Instant now);
}
