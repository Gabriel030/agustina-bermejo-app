package com.red_escuela.backend.enrollment.service;

import java.time.LocalDate;

public record WaitlistQueueSnapshot(Long id, String name, String termName, Integer capacity, long occupied, long waiting,
                                    LocalDate nextExpiry) {

    public Long free() {
        return capacity == null ? null : Math.max(capacity - occupied, 0L);
    }
}
