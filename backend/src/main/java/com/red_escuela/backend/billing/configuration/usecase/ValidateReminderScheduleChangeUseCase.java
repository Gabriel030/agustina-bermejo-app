package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import java.time.LocalDate;
import java.util.List;

public interface ValidateReminderScheduleChangeUseCase {
    List<PolicyViolation> validateChange(ReminderScheduleTarget target, ReminderSchedule schedule, LocalDate effectiveFrom, String changeReason);
}
