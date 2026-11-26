CREATE TABLE guarantee_incident_projection (
  loan_id CHAR(36) NOT NULL,
  incident_id CHAR(36) NOT NULL,
  decision VARCHAR(30) NULL,
  amount DECIMAL(12,2) NULL,
  currency CHAR(3) NULL,
  PRIMARY KEY (loan_id, incident_id)
) ENGINE=InnoDB;

CREATE TABLE guarantee_closures (
  loan_id CHAR(36) PRIMARY KEY,
  resolved_incidents TEXT NOT NULL
) ENGINE=InnoDB;
