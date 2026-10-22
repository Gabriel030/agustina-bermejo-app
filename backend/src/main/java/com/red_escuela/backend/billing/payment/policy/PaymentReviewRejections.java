package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.exception.PaymentDateInFutureException;
import com.red_escuela.backend.billing.payment.exception.PaymentRejectionReasonRequiredException;
import com.red_escuela.backend.billing.payment.exception.PaymentRejectionReasonTooLongException;
import com.red_escuela.backend.billing.payment.exception.PaymentReviewNoteTooLongException;
import com.red_escuela.backend.billing.payment.exception.VerifiedPaymentAmountInvalidException;
import com.red_escuela.backend.billing.payment.exception.VerifiedPaymentCurrencyMismatchException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class PaymentReviewRejections {

    private PaymentReviewRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return switch (violations.get(0).code()) {
            case "VERIFIED_PAYMENT_AMOUNT_INVALID" -> new VerifiedPaymentAmountInvalidException(violations);
            case "VERIFIED_PAYMENT_CURRENCY_MISMATCH" -> new VerifiedPaymentCurrencyMismatchException(violations);
            case "PAYMENT_DATE_IN_FUTURE" -> new PaymentDateInFutureException(violations);
            case "PAYMENT_REVIEW_NOTE_TOO_LONG" -> new PaymentReviewNoteTooLongException(violations);
            case "PAYMENT_REJECTION_REASON_REQUIRED" -> new PaymentRejectionReasonRequiredException(violations);
            case "PAYMENT_REJECTION_REASON_TOO_LONG" -> new PaymentRejectionReasonTooLongException(violations);
            default -> throw new IllegalStateException("Unmapped payment review violation " + violations.get(0).code());
        };
    }
}
