package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Percentage;

public record PlannedReminderLateFee(Percentage dailyRate, int graceDays) {
}
