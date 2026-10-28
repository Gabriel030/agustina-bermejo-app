package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record PlannedLateFeeReinstatement(Long originInvoiceId, Long correctionId, LocalDate firstDay, LocalDate lastDay, int chargedDays, NonNegativeMoney amount,
                                          LocalDate issueDate, LocalDate debitNoteDueDate) {
}
