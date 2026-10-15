package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import java.util.List;

public interface ListReminderScheduleVersionsUseCase {
    List<ConfigurationVersion> listVersions(ReminderScheduleTarget target);
}
