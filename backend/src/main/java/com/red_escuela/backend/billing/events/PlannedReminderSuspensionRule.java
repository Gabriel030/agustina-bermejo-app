package com.red_escuela.backend.billing.events;

public record PlannedReminderSuspensionRule(int afterDays, int afterInvoiceCount) {
}
