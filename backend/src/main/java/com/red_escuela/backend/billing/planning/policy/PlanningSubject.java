package com.red_escuela.backend.billing.planning.policy;

public enum PlanningSubject {
    PROGRAM("la inscripción no está ENROLLED ni PAUSED"),
    COURSE("la inscripción al curso no está ENROLLED");

    private final String inactiveDetail;

    PlanningSubject(String inactiveDetail) {
        this.inactiveDetail = inactiveDetail;
    }

    public String inactiveDetail() {
        return inactiveDetail;
    }
}
