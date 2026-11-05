package com.red_escuela.backend.enrollment.api;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record WithdrawnEnrollmentFacts(Long studentId, Long programEnrollmentId, Long courseEnrollmentId, LocalDate effectiveDate, LocalDate registeredOn,
                                      WithdrawalCause cause) {

    public WithdrawnEnrollmentFacts {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(registeredOn, "registeredOn");
        Objects.requireNonNull(cause, "cause");
        if ((programEnrollmentId == null) == (courseEnrollmentId == null)) {
            throw new IllegalArgumentException("Exactly one of programEnrollmentId and courseEnrollmentId is required");
        }
    }

    public static WithdrawnEnrollmentFacts ofProgramEnrollment(Long studentId, Long programEnrollmentId, LocalDate effectiveDate, LocalDate registeredOn,
                                                                WithdrawalCause cause) {
        return new WithdrawnEnrollmentFacts(studentId, programEnrollmentId, null, effectiveDate, registeredOn, cause);
    }

    public static WithdrawnEnrollmentFacts ofCourseEnrollment(Long studentId, Long courseEnrollmentId, LocalDate effectiveDate, LocalDate registeredOn,
                                                                WithdrawalCause cause) {
        return new WithdrawnEnrollmentFacts(studentId, null, courseEnrollmentId, effectiveDate, registeredOn, cause);
    }

    public Optional<Long> programEnrollment() {
        return Optional.ofNullable(programEnrollmentId);
    }

    public Optional<Long> courseEnrollment() {
        return Optional.ofNullable(courseEnrollmentId);
    }
}
