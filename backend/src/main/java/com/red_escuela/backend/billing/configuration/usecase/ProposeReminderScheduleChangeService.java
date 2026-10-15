package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.service.ReminderScheduleTargets;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

@UseCase
public class ProposeReminderScheduleChangeService implements ProposeReminderScheduleChangeUseCase {
    private final ProposeBillingConfigurationChangeUseCase proposeChangeUseCase;
    private final ReminderScheduleTargets targets;

    public ProposeReminderScheduleChangeService(ProposeBillingConfigurationChangeUseCase proposeChangeUseCase, ReminderScheduleTargets targets) {
        this.proposeChangeUseCase = proposeChangeUseCase;
        this.targets = targets;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<ConfigurationVersion> proposeChange(ReminderScheduleTarget target, ReminderSchedule schedule, LocalDate effectiveFrom, String changeReason) {
        return proposeChangeUseCase.proposeChange(targets.requireExistingTopic(target), targets.requestedValuesOf(target.group(), schedule), effectiveFrom, changeReason);
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<ConfigurationVersion> proposeExceptionRemoval(ReminderScheduleTarget target, LocalDate effectiveFrom, String changeReason) {
        return proposeChangeUseCase.proposeChange(targets.requireExistingTopic(target), targets.valuesOf(target.group(), ReminderSchedule.followingGroup()), effectiveFrom, changeReason);
    }
}
