package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.exception.ChargebackDateInvalidException;
import com.red_escuela.backend.billing.payment.exception.ChargebackMustBeForTheFullAmountException;
import com.red_escuela.backend.billing.payment.exception.ChargebackReferenceInvalidException;
import com.red_escuela.backend.billing.payment.exception.PaymentAlreadyRefundedException;
import com.red_escuela.backend.billing.payment.exception.PaymentHasPendingRefundException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class PaymentReversalRejections {

    private PaymentReversalRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return switch (violations.get(0).code()) {
            case "PAYMENT_ALREADY_REFUNDED" -> new PaymentAlreadyRefundedException(violations);
            case "PAYMENT_HAS_PENDING_REFUND" -> new PaymentHasPendingRefundException(violations);
            case "CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT" -> new ChargebackMustBeForTheFullAmountException(violations);
            case "CHARGEBACK_REFERENCE_INVALID" -> new ChargebackReferenceInvalidException(violations);
            case "CHARGEBACK_DATE_INVALID" -> new ChargebackDateInvalidException(violations);
            default -> throw new IllegalStateException("Unmapped payment reversal violation " + violations.get(0).code());
        };
    }
}
