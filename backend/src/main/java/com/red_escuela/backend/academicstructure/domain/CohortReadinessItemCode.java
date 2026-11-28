package com.red_escuela.backend.academicstructure.domain;

public enum CohortReadinessItemCode {
    CURRENT_PLAN(true),
    FIRST_YEAR_SECTIONS(true),
    SECTION_PRECEPTORS(true),
    COHORT_RESPONSIBLE(true),
    HOLIDAYS_LOADED(true),
    BILLING_TEMPLATE(true);

    private final boolean blocking;

    CohortReadinessItemCode(boolean blocking) {
        this.blocking = blocking;
    }

    public boolean blocking() {
        return blocking;
    }
}
