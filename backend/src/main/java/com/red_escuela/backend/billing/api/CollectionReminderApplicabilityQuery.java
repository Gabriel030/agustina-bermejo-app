package com.red_escuela.backend.billing.api;

import java.time.LocalDate;

public interface CollectionReminderApplicabilityQuery {

    boolean isStillApplicable(Long reminderId, LocalDate date);
}
