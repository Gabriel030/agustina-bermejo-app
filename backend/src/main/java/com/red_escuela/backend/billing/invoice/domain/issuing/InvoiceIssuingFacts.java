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
        Long priceVersionId,
        Long existingInvoiceId,
        Integer templateEnrollmentFeeCount,
        StudentSnapshot studentSnapshot,
        boolean enrollmentMatchesRecipient,
        NonNegativeMoney marketingDiscountAmount) {

    public InvoiceIssuingFacts(LocalDate issueDate, Currency offeringCurrency, NonNegativeMoney chargeAmount, Long priceVersionId, Long existingInvoiceId,
                               Integer templateEnrollmentFeeCount, StudentSnapshot studentSnapshot, boolean enrollmentMatchesRecipient) {
        this(issueDate, offeringCurrency, chargeAmount, priceVersionId, existingInvoiceId, templateEnrollmentFeeCount, studentSnapshot,
                enrollmentMatchesRecipient, null);
    }

    public InvoiceIssuingFacts {
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(offeringCurrency, "offeringCurrency");
        Objects.requireNonNull(chargeAmount, "chargeAmount");
        Objects.requireNonNull(studentSnapshot, "studentSnapshot");
        marketingDiscountAmount = marketingDiscountAmount == null ? NonNegativeMoney.zero(chargeAmount.value().currency()) : marketingDiscountAmount;
    }
}
