package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.Objects;

public record WithdrawnEnrollmentDebtLine(Long studentId, Long personId, Long programEnrollmentId, Long courseEnrollmentId, LocalDate withdrawalEffectiveDate,
                                          Long invoiceId, boolean enrollmentFee, LocalDate dueDate, Money balance, String enrollmentName) {

    public WithdrawnEnrollmentDebtLine {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(withdrawalEffectiveDate, "withdrawalEffectiveDate");
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(balance, "balance");
        if ((programEnrollmentId == null) == (courseEnrollmentId == null)) {
            throw new IllegalArgumentException("Exactly one of programEnrollmentId and courseEnrollmentId is required");
        }
    }
}
