package com.red_escuela.backend.notification.delivery.domain;

import java.time.LocalDate;
import java.util.Objects;

public record DispatchItem(String key, LocalDate dueDate, boolean plannedBeforeDueDate, int priority) {

    public DispatchItem {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A dispatch item needs a key");
        }
        Objects.requireNonNull(dueDate, "dueDate");
        if (priority < DeliveryEnvelope.HIGHEST_PRIORITY) {
            throw new IllegalArgumentException("A priority is at least " + DeliveryEnvelope.HIGHEST_PRIORITY);
        }
    }

    public boolean isNoLongerTimelyOn(LocalDate dispatchDate) {
        return plannedBeforeDueDate && dispatchDate.isAfter(dueDate);
    }

    public boolean precedesDueDateWhenSentOn(LocalDate dispatchDate) {
        return dispatchDate.isBefore(dueDate);
    }
}
