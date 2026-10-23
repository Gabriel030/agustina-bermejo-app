package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.payment.exception.RefundAmountInvalidException;
import com.red_escuela.backend.billing.payment.exception.RefundBlockedByOverdueDebtException;
import com.red_escuela.backend.billing.payment.exception.RefundCompletionInvalidException;
import com.red_escuela.backend.billing.payment.exception.RefundDestinationInvalidException;
import com.red_escuela.backend.billing.payment.exception.RefundDestinationRequiredException;
import com.red_escuela.backend.billing.payment.exception.RefundExceedsAvailableAmountException;
import com.red_escuela.backend.billing.payment.exception.RefundFailureReasonInvalidException;
import com.red_escuela.backend.billing.payment.exception.RefundMethodMismatchException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class RefundRejections {

    private RefundRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return switch (violations.get(0).code()) {
            case "REFUND_AMOUNT_INVALID" -> new RefundAmountInvalidException(violations);
            case "REFUND_DESTINATION_INVALID" -> new RefundDestinationInvalidException(violations);
            case "REFUND_DESTINATION_REQUIRED" -> new RefundDestinationRequiredException(violations);
            case "REFUND_BLOCKED_BY_OVERDUE_DEBT" -> new RefundBlockedByOverdueDebtException(violations);
            case "INSUFFICIENT_ACCOUNT_CREDIT" -> new InsufficientAccountCreditException();
            case "REFUND_EXCEEDS_AVAILABLE_AMOUNT" -> new RefundExceedsAvailableAmountException(violations);
            case "REFUND_COMPLETION_INVALID" -> new RefundCompletionInvalidException(violations);
            case "REFUND_METHOD_MISMATCH" -> new RefundMethodMismatchException(violations);
            case "REFUND_FAILURE_REASON_INVALID" -> new RefundFailureReasonInvalidException(violations);
            default -> throw new IllegalStateException("Unmapped refund violation " + violations.get(0).code());
        };
    }
}
