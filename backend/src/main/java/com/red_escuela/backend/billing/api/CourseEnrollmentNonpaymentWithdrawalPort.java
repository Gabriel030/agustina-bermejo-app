package com.red_escuela.backend.billing.api;

import java.time.LocalDate;

public interface CourseEnrollmentNonpaymentWithdrawalPort {

    void withdrawForNonpayment(Long courseEnrollmentId, LocalDate effectiveDate);
}
