package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.identity.entity.Person;

public final class ApproveProgramEnrollmentApplicationCommand {

    private final Long id;
    private final Person decidedBy;
    private String idempotencyKey;

    public ApproveProgramEnrollmentApplicationCommand(Long id, Person decidedBy) {
        this.id = id;
        this.decidedBy = decidedBy;
    }

    public ApproveProgramEnrollmentApplicationCommand withIdempotencyKey(String key) {
        this.idempotencyKey = key;
        return this;
    }

    public Long id() {
        return id;
    }

    public Person decidedBy() {
        return decidedBy;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
