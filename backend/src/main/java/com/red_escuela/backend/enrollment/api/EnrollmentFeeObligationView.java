package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Money;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.YearMonth;

public record EnrollmentFeeObligationView(Long templateId, int slot, int slotCount, YearMonth periodFrom, YearMonth periodTo, String status, Long invoiceId,
                                          LocalDate dueDate, Money remainingBalance, boolean satisfied, @Nullable AdvanceEnrollmentFeeView advance) {
}
