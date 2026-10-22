package com.red_escuela.backend.notification.delivery.domain;

import java.util.List;

public record DispatchCandidate(long deliveryId, List<DispatchItem> items) {

    public DispatchCandidate {
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("A dispatch candidate needs at least one item");
        }
    }
}
