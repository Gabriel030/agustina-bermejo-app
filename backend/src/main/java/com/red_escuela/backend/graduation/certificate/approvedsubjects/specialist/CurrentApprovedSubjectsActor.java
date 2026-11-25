package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsActor;
import org.springframework.stereotype.Component;

@Component
public class CurrentApprovedSubjectsActor implements ApprovedSubjectsActor {

    private final CurrentActorFinder currentActorFinder;

    public CurrentApprovedSubjectsActor(CurrentActorFinder currentActorFinder) {
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public Long currentStudentId() {
        return currentActorFinder.currentStudentId();
    }

    @Override
    public Long currentPersonId() {
        return currentActorFinder.currentPersonId();
    }
}
