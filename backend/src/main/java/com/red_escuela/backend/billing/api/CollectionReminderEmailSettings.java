package com.red_escuela.backend.billing.api;

import java.time.LocalTime;

public record CollectionReminderEmailSettings(LocalTime windowStart, LocalTime windowEnd, int dailyCap) {
}
