package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.api.CourseEnrollmentNonpaymentWithdrawalPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class CourseEnrollmentNonpaymentWithdrawalAdapter implements CourseEnrollmentNonpaymentWithdrawalPort {

    private final CourseEnrollmentService courseEnrollmentService;

    public CourseEnrollmentNonpaymentWithdrawalAdapter(CourseEnrollmentService courseEnrollmentService) {
        this.courseEnrollmentService = courseEnrollmentService;
    }

    @Override
    public void withdrawForNonpayment(Long courseEnrollmentId, LocalDate effectiveDate) {
        courseEnrollmentService.withdrawForNonpayment(courseEnrollmentId, effectiveDate);
    }
}
