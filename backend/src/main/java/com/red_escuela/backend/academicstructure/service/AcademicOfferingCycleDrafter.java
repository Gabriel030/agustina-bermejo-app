package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AcademicOfferingCycleDrafter {

    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public AcademicOfferingCycleDrafter(CurrentActorFinder currentActorFinder, Clock clock) {
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    public AcademicOfferingCycle draftFor(Term term) {
        var cycle = new AcademicOfferingCycle();
        cycle.setTerm(term);
        cycle.setCreatedBy(currentActorFinder.currentPerson());
        cycle.setCreatedAt(LocalDateTime.now(clock));
        return cycle;
    }
}
