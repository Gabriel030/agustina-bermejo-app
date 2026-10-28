package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpact;
import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpactQuery;
import com.red_escuela.backend.billing.collections.service.CollectionReminderImpactSimulator;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class GetCollectionReminderImpactService implements GetCollectionReminderImpactUseCase {
    private final CollectionReminderImpactSimulator simulator;

    public GetCollectionReminderImpactService(CollectionReminderImpactSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public CollectionReminderImpact getImpactOf(CollectionReminderImpactQuery query) {
        return simulator.simulateImpactOf(query);
    }
}
