package com.red_escuela.backend.billing.api;

public interface EnrollmentDebtGuard {

    void verifyNoOutstandingDebt(Long studentId);
}
