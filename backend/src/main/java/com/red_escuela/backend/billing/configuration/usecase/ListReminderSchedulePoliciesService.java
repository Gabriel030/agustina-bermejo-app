package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ReminderSchedulePolicies;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;

@UseCase
public class ListReminderSchedulePoliciesService implements ListReminderSchedulePoliciesUseCase {
    private final BillingConfigurationReader reader;
    private final Clock clock;

    public ListReminderSchedulePoliciesService(BillingConfigurationReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public ReminderSchedulePolicies listPolicies() {
        LocalDate today = LocalDate.now(clock);
        return ReminderSchedulePolicies.of(Arrays.stream(TeachingGroup.values()).flatMap(group -> reader.listReminderScheduleTopicsEffectiveOn(group, today).stream()).toList());
    }
}
