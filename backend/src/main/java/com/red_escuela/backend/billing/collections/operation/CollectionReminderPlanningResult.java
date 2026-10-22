package com.red_escuela.backend.billing.collections.operation;

import java.util.List;

public record CollectionReminderPlanningResult(int candidates, int planned, int alreadyPlanned, int nothingToSend, int failed, List<PlanningError> errors) {

    public CollectionReminderPlanningResult {
        errors = List.copyOf(errors);
    }
}
