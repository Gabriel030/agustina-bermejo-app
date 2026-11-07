package com.red_escuela.backend.enrollment.api;

public interface CourseEnrollmentNonpaymentWithdrawalPort {

    void withdrawForNonpayment(Long courseEnrollmentId);
}
