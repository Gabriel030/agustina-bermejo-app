package com.red_escuela.backend.billing.operation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AnnulledLateFeeReinstatement {

    List<Long> reinstateLateFeesAnnulledByTheReversedPayment(Collection<Long> reopenedInvoiceIds, LocalDate businessDate);
}
