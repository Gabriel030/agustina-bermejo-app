package com.red_escuela.backend.billing.collections.policy;

public record NewEnrollmentDebtCheck(Long studentId, boolean blockNewEnrollmentsWhenOverdue, boolean hasOverdueDebt) {
}
