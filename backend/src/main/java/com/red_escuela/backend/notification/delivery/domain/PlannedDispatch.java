package com.red_escuela.backend.notification.delivery.domain;

import java.time.LocalDate;
import java.util.List;

public record PlannedDispatch(long deliveryId, LocalDate scheduledFor, int priority, List<DispatchItem> keptItems, List<DispatchItem> droppedItems,
                              boolean precedesDueDate) {

    public PlannedDispatch {
        keptItems = List.copyOf(keptItems);
        droppedItems = List.copyOf(droppedItems);
    }

    public boolean dropsItems() {
        return !droppedItems.isEmpty();
    }
}
