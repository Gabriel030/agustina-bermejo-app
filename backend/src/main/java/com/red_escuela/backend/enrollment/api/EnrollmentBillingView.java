package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;

import java.time.LocalDate;

public record EnrollmentBillingView(Long id, Long studentId, Long programId, Long courseId, Long billingTemplateId,
                                    ProgramEnrollmentStatus programStatus, EnrollmentStatus courseStatus,
                                    LocalDate withdrawalEffectiveDate, LocalDate withdrawalRegisteredOn) {
}
