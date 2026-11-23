package com.red_escuela.backend.shared.module;

import java.time.LocalDate;

public interface CourseEnrollmentNonpaymentWithdrawalPort {

    void withdrawForNonpayment(Long courseEnrollmentId, LocalDate effectiveDate);
}
