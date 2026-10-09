package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.YearMonth;
import java.util.Objects;

public record InvoiceLineDraft(
        FeeType feeType,
        String concept,
        YearMonth period,
        Long programEnrollmentId,
        Long courseEnrollmentId,
        Long subjectId,
        Long examBoardRegistrationId,
        Long priceRevisionId,
        String chargeKey,
        NonNegativeMoney amount,
        Long templateId,
        Integer enrollmentFeeSlot) {

    public InvoiceLineDraft(FeeType feeType, String concept, YearMonth period, Long programEnrollmentId, Long courseEnrollmentId, Long subjectId,
                            Long examBoardRegistrationId, Long priceRevisionId, String chargeKey, NonNegativeMoney amount) {
        this(feeType, concept, period, programEnrollmentId, courseEnrollmentId, subjectId, examBoardRegistrationId, priceRevisionId, chargeKey, amount,
                null, null);
    }


    private static final int MAX_CONCEPT_LENGTH = 200;
    private static final int MAX_CHARGE_KEY_LENGTH = 120;

    public InvoiceLineDraft {
        Objects.requireNonNull(feeType, "feeType");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(amount, "amount");
        if (concept == null || concept.isBlank() || concept.length() > MAX_CONCEPT_LENGTH) {
            throw new IllegalArgumentException("concept must have between 1 and " + MAX_CONCEPT_LENGTH + " characters");
        }
        if (chargeKey == null || chargeKey.isBlank() || chargeKey.length() > MAX_CHARGE_KEY_LENGTH) {
            throw new IllegalArgumentException("chargeKey must have between 1 and " + MAX_CHARGE_KEY_LENGTH + " characters");
        }
        if (amount.value().isZero()) {
            throw new IllegalArgumentException("A line amount must be greater than zero");
        }
        if (programEnrollmentId != null && courseEnrollmentId != null) {
            throw new IllegalArgumentException("A line belongs to at most one enrollment");
        }
        if ((feeType == FeeType.MONTHLY_INSTALLMENT || feeType == FeeType.ENROLLMENT_FEE) && priceRevisionId == null) {
            throw new IllegalArgumentException("A priced charge needs its price revision");
        }
    }
}
