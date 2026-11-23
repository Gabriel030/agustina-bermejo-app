package com.red_escuela.backend.enrollment.api;

public interface EnrollmentDebtGuardPort {

    void verifyNoOutstandingDebt(Long studentId);
}
