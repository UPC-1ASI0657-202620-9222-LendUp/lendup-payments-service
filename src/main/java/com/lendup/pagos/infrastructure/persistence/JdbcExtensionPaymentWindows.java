package com.lendup.pagos.infrastructure.persistence;

import com.lendup.pagos.application.port.out.ExtensionPaymentWindows;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExtensionPaymentWindows implements ExtensionPaymentWindows {
  private final JdbcClient db;

  public JdbcExtensionPaymentWindows(JdbcClient db) { this.db = db; }

  @Override public boolean open(UUID loanId, UUID changeId, Instant expiresAt) {
    return db.sql("""
        INSERT IGNORE INTO extension_payment_windows (change_id, loan_id, expires_at, rejected)
        VALUES (?, ?, ?, false)
        """).params(changeId.toString(), loanId.toString(), utc(expiresAt)).update() == 1;
  }

  @Override public void reject(UUID loanId, UUID changeId) {
    db.sql("""
        INSERT INTO extension_payment_windows (change_id, loan_id, expires_at, rejected)
        VALUES (?, ?, NULL, true) ON DUPLICATE KEY UPDATE rejected = true
        """).params(changeId.toString(), loanId.toString()).update();
  }

  @Override public boolean active(UUID loanId, UUID changeId, Instant now) {
    if (changeId == null) return false;
    return db.sql("""
        SELECT COUNT(*) FROM extension_payment_windows
        WHERE change_id = ? AND loan_id = ? AND rejected = false AND expires_at > ?
        """).params(changeId.toString(), loanId.toString(), utc(now)).query(Integer.class).single() == 1;
  }

  private static LocalDateTime utc(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
}
