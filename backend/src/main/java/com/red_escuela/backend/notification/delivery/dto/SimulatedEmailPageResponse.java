package com.red_escuela.backend.notification.delivery.dto;

import java.util.List;

public record SimulatedEmailPageResponse(List<SimulatedEmailResponse> items, int page, int size, long totalElements) {

    public SimulatedEmailPageResponse {
        items = List.copyOf(items);
    }
}
