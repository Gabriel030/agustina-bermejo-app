package com.red_escuela.backend.billing.planning.domain;

import java.util.EnumSet;
import java.util.Set;

public enum InvoicePlanningScope {
    ALL(EnumSet.allOf(PlanningSection.class)),
    MONTHLY_INSTALLMENT_ONLY(EnumSet.of(PlanningSection.MONTHLY_INSTALLMENT)),
    ENROLLMENT_FEE_ONLY(EnumSet.of(PlanningSection.ENROLLMENT_FEES));

    private final Set<PlanningSection> sections;

    InvoicePlanningScope(Set<PlanningSection> sections) {
        this.sections = sections;
    }

    public Set<PlanningSection> sections() {
        return sections;
    }
}
