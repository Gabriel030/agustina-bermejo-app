package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import java.time.LocalDate;

public interface ProposeReminderScheduleChangeUseCase {
    BillingOperationOutcome<ConfigurationVersion> proposeChange(ReminderScheduleTarget target, ReminderSchedule schedule, LocalDate effectiveFrom, String changeReason);

    BillingOperationOutcome<ConfigurationVersion> proposeExceptionRemoval(ReminderScheduleTarget target, LocalDate effectiveFrom, String changeReason);
}
