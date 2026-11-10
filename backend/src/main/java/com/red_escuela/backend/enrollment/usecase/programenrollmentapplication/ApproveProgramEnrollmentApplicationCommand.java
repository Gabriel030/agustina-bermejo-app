package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.identity.entity.Person;

public record ApproveProgramEnrollmentApplicationCommand(Long id, Person decidedBy, String idempotencyKey) {

    public ApproveProgramEnrollmentApplicationCommand(Long id, Person decidedBy) {
        this(id, decidedBy, null);
    }
}
