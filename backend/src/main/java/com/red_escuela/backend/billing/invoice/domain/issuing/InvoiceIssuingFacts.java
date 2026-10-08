package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;

public record InvoiceIssuingFacts(
        LocalDate issueDate,
        Currency offeringCurrency,
        NonNegativeMoney chargeAmount,
        Long priceRevisionId,
        Long existingInvoiceId,
        Integer templateEnrollmentFeeCount,
        StudentSnapshot studentSnapshot,
        boolean enrollmentMatchesRecipient) {

    public InvoiceIssuingFacts {
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(offeringCurrency, "offeringCurrency");
        Objects.requireNonNull(chargeAmount, "chargeAmount");
        Objects.requireNonNull(studentSnapshot, "studentSnapshot");
    }
}
