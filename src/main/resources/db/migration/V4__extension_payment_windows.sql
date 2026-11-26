CREATE TABLE extension_payment_windows (
  change_id CHAR(36) PRIMARY KEY,
  loan_id CHAR(36) NOT NULL,
  expires_at DATETIME(6) NULL,
  rejected BOOLEAN NOT NULL
) ENGINE=InnoDB;
