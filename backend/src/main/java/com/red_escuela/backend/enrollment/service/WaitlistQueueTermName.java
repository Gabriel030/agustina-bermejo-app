package com.red_escuela.backend.enrollment.service;

final class WaitlistQueueTermName {

    private WaitlistQueueTermName() {
    }

    static String of(String description, Integer year) {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return year == null ? "" : String.valueOf(year);
    }
}
