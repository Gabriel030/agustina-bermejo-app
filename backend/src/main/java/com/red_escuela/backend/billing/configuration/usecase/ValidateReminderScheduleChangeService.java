package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.service.ReminderScheduleTargets;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.List;

@UseCase
public class ValidateReminderScheduleChangeService implements ValidateReminderScheduleChangeUseCase {
    private final ValidateBillingConfigurationChangeUseCase validateChangeUseCase;
    private final ReminderScheduleTargets targets;

    public ValidateReminderScheduleChangeService(ValidateBillingConfigurationChangeUseCase validateChangeUseCase, ReminderScheduleTargets targets) {
        this.validateChangeUseCase = validateChangeUseCase;
        this.targets = targets;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PolicyViolation> validateChange(ReminderScheduleTarget target, ReminderSchedule schedule, LocalDate effectiveFrom, String changeReason) {
        return validateChangeUseCase.validateChange(targets.requireExistingTopic(target), targets.valuesOf(target.group(), schedule), effectiveFrom, changeReason);
    }
}
