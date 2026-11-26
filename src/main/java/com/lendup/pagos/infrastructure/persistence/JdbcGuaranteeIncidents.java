package com.lendup.pagos.infrastructure.persistence;

import com.lendup.pagos.application.port.in.PaymentViews.IncidenciaResueltaCommand;
import com.lendup.pagos.application.port.out.GuaranteeIncidents;
import com.lendup.pagos.domain.model.DecisionGarantia;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGuaranteeIncidents implements GuaranteeIncidents {
  private final JdbcClient db;

  public JdbcGuaranteeIncidents(JdbcClient db) { this.db = db; }

  @Override public boolean report(UUID loanId, UUID incidentId) {
    return db.sql("INSERT IGNORE INTO guarantee_incident_projection (loan_id, incident_id) VALUES (?, ?)")
        .params(loanId.toString(), incidentId.toString()).update() == 1;
  }

  @Override public Resolution resolve(IncidenciaResueltaCommand command, String currency) {
    boolean first = report(command.prestamoId(), command.incidenciaId());
    int changed = db.sql("""
        UPDATE guarantee_incident_projection SET decision = :decision, amount = :amount, currency = :currency
        WHERE loan_id = :loan AND incident_id = :incident AND decision IS NULL
        """).param("decision", command.decision().name()).param("amount", command.montoAfectado())
        .param("currency", currency).param("loan", command.prestamoId().toString()).param("incident", command.incidenciaId().toString())
        .update();
    return changed == 0 ? Resolution.DUPLICATE : first ? Resolution.NEW : Resolution.WAS_OPEN;
  }

  @Override public void close(UUID loanId, List<UUID> resolved) {
    String manifest = resolved.stream().map(UUID::toString).sorted().distinct().collect(Collectors.joining(","));
    db.sql("INSERT IGNORE INTO guarantee_closures (loan_id, resolved_incidents) VALUES (?, ?)")
        .params(loanId.toString(), manifest).update();
  }

  @Override public boolean ready(UUID loanId) {
    var expected = db.sql("SELECT resolved_incidents FROM guarantee_closures WHERE loan_id = ?")
        .param(loanId.toString()).query(String.class).optional();
    if (expected.isEmpty()) return false;
    List<String> pending = db.sql("SELECT incident_id FROM guarantee_incident_projection WHERE loan_id = ? AND decision IS NULL")
        .param(loanId.toString()).query(String.class).list();
    if (!pending.isEmpty()) return false;
    var actual = decisions(loanId).stream().map(d -> d.incidenciaId().toString()).collect(Collectors.toSet());
    return expected.get().isEmpty() || actual.containsAll(Arrays.asList(expected.get().split(",")));
  }

  @Override public List<IncidenciaResueltaCommand> decisions(UUID loanId) {
    return db.sql("""
        SELECT incident_id, decision, amount FROM guarantee_incident_projection
        WHERE loan_id = ? AND decision IS NOT NULL ORDER BY incident_id
        """).param(loanId.toString()).query((row, n) -> new IncidenciaResueltaCommand(
        UUID.fromString(row.getString("incident_id")), loanId, DecisionGarantia.valueOf(row.getString("decision")),
        row.getBigDecimal("amount"))).list();
  }
}
