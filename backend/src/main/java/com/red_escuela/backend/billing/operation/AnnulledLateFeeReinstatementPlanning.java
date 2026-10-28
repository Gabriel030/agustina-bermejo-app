package com.red_escuela.backend.billing.operation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AnnulledLateFeeReinstatementPlanning {

    List<Long> findCorrectionIdsAwaitingReinstatement(Long originInvoiceId);

    Optional<PlannedLateFeeReinstatement> planReinstatement(Long originInvoiceId, Long correctionId, boolean originIsOverdue, LocalDate businessDate);
}
