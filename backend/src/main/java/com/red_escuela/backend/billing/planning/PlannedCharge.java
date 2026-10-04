package com.red_escuela.backend.billing.planning;

import java.time.LocalDate;
import java.time.YearMonth;

public record PlannedCharge(ChargeKind kind, YearMonth period, LocalDate dueDate, LocalDate priceDate, Long templateId, Integer slot, String reason, boolean entry, boolean upfront) {
}
