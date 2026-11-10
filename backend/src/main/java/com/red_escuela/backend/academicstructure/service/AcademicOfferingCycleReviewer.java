package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AcademicOfferingCycleReviewer {

    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public AcademicOfferingCycleReviewer(CurrentActorFinder currentActorFinder, Clock clock) {
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    public void approve(AcademicOfferingCycle cycle, String idempotencyKey) {
        cycle.approve(currentActorFinder.currentPerson(), LocalDateTime.now(clock));
        cycle.recordDecisionKey(idempotencyKey);
    }

    public void reject(AcademicOfferingCycle cycle, String reason, String idempotencyKey) {
        cycle.reject(currentActorFinder.currentPerson(), reason, LocalDateTime.now(clock));
        cycle.recordDecisionKey(idempotencyKey);
    }
}
