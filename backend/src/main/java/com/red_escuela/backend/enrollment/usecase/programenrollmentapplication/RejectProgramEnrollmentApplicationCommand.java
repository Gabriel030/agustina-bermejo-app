package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.identity.entity.Person;

public final class RejectProgramEnrollmentApplicationCommand {

    private final Long id;
    private final Person decidedBy;
    private final String reason;
    private String idempotencyKey;

    public RejectProgramEnrollmentApplicationCommand(Long id, Person decidedBy, String reason) {
        this.id = id;
        this.decidedBy = decidedBy;
        this.reason = reason;
    }

    public RejectProgramEnrollmentApplicationCommand withIdempotencyKey(String key) {
        this.idempotencyKey = key;
        return this;
    }

    public Long id() {
        return id;
    }

    public Person decidedBy() {
        return decidedBy;
    }

    public String reason() {
        return reason;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
