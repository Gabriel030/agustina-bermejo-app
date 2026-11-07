package com.red_escuela.backend.billing.invoice.policy;

public record ExamFeeIssuanceCheck(Long studentId, boolean hasOverdueDebt) {
}
