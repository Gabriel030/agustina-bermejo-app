package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

public final class PlanningFixtures_ {
    private PlanningFixtures_() {
    }

    public static ProgramYearBillingTemplate template(long id, int year) {
        return PlanningFixtures.template(id, year, 10, 1, 2, null);
    }

    public static ProgramYearBillingTemplate template() {
        return PlanningFixtures.template(1, 2026, 10, 1, 2, null);
    }
}
