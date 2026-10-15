package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.exception.CashPaymentAmountInvalidException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentBackdateReasonInvalidException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentBackdateReasonRequiredException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentDateTooOldException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentPreviewOutdatedException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentReceiptNumberInvalidException;
import com.red_escuela.backend.billing.payment.exception.IdempotencyKeyInvalidException;
import com.red_escuela.backend.billing.payment.exception.PaymentDateInFutureException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class CashPaymentRejections {

    private CashPaymentRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return switch (violations.get(0).code()) {
            case "IDEMPOTENCY_KEY_INVALID" -> new IdempotencyKeyInvalidException(violations);
            case "CASH_PAYMENT_AMOUNT_INVALID" -> new CashPaymentAmountInvalidException(violations);
            case "CASH_PAYMENT_RECEIPT_NUMBER_INVALID" -> new CashPaymentReceiptNumberInvalidException(violations);
            case "PAYMENT_DATE_IN_FUTURE" -> new PaymentDateInFutureException(violations);
            case "CASH_PAYMENT_DATE_TOO_OLD" -> new CashPaymentDateTooOldException(violations);
            case "CASH_PAYMENT_BACKDATE_REASON_INVALID" -> new CashPaymentBackdateReasonInvalidException(violations);
            case "CASH_PAYMENT_BACKDATE_REASON_REQUIRED" -> new CashPaymentBackdateReasonRequiredException(violations);
            case "CASH_PAYMENT_PREVIEW_OUTDATED" -> new CashPaymentPreviewOutdatedException(violations);
            default -> throw new IllegalStateException("Unmapped cash payment violation " + violations.get(0).code());
        };
    }
}
