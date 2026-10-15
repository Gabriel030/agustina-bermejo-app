package com.red_escuela.backend.billing.planning.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import com.red_escuela.backend.billing.invoice.domain.FeeType;

public record PlannedInvoice(FeeType kind, YearMonth period, LocalDate dueDate, LocalDate priceDate, Long templateId, Integer slot, String reason, boolean entry, boolean upfront) {
}
