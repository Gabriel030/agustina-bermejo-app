package com.red_escuela.backend.evaluation.domain;

public enum ClosureViewerProfile {
    TEACHER,
    STAFF;

    public boolean seesAdministrativeDetail() {
        return this == STAFF;
    }
}
