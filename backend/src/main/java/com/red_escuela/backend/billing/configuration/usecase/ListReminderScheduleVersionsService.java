package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.service.ReminderScheduleTargets;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@UseCase
public class ListReminderScheduleVersionsService implements ListReminderScheduleVersionsUseCase {
    private final ListConfigurationVersionHistoryUseCase historyUseCase;
    private final ReminderScheduleTargets targets;

    public ListReminderScheduleVersionsService(ListConfigurationVersionHistoryUseCase historyUseCase, ReminderScheduleTargets targets) {
        this.historyUseCase = historyUseCase;
        this.targets = targets;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<ConfigurationVersion> listVersions(ReminderScheduleTarget target) {
        return historyUseCase.listVersionHistory(targets.requireExistingTopic(target));
    }
}
