package com.red_escuela.backend.billing.collections.operation;

import java.util.List;
import java.util.Map;

public record CollectionReminderPlanningResult(int candidates, int planned, int alreadyPlanned, int nothingToSend, int failed, List<Map<String, Object>> errors) {

    public CollectionReminderPlanningResult {
        errors = List.copyOf(errors);
    }
}
