package com.lendup.pagos.application.port.out;

import com.lendup.pagos.application.port.in.PaymentViews.IncidenciaResueltaCommand;
import java.util.List;
import java.util.UUID;

public interface GuaranteeIncidents {
  enum Resolution { NEW, WAS_OPEN, DUPLICATE }
  boolean report(UUID loanId, UUID incidentId);
  Resolution resolve(IncidenciaResueltaCommand command, String currency);
  void close(UUID loanId, List<UUID> resolved);
  boolean ready(UUID loanId);
  List<IncidenciaResueltaCommand> decisions(UUID loanId);
}
