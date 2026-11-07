package com.red_escuela.backend.billing.collections.policy;

public record ExamFeeIssuanceCheck(Long studentId, boolean hasOverdueDebt) {
}
