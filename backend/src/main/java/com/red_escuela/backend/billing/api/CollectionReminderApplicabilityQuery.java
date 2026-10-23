package com.red_escuela.backend.billing.api;

import java.time.LocalDate;
import java.util.Set;

public interface CollectionReminderApplicabilityQuery {

    boolean isStillApplicable(Long reminderId, LocalDate date);

    boolean isStillApplicable(Long reminderId, LocalDate date, Set<Long> noticedInvoiceIds);
}
