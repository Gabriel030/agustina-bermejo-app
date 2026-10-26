package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.RefundEligibility;
import com.red_escuela.backend.billing.payment.domain.RefundablePaymentView;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record RefundEligibilityResponse(String currency, Money unallocatedCredit, Money reservedByPendingRefunds, Money availableToRefund, boolean hasOverdueDebt,
                                        RefundBlockResponse blockedBy, List<RefundablePaymentResponse> payments) {

    public record RefundablePaymentResponse(Long paymentId, LocalDate paidOn, String methodType, Money credited, Money refunded, Money pending, Money refundableNow,
                                  String receiptDestinationAccount, String receiptDestinationHolder) {

        static RefundablePaymentResponse from(RefundablePaymentView view) {
            return new RefundablePaymentResponse(view.paymentId(), view.paidOn(), view.methodType().name(), view.credited().value(), view.refunded().value(), view.pending().value(),
                    view.refundableNow().value(), view.receiptDestination().account(), view.receiptDestination().holder());
        }
    }

    public static RefundEligibilityResponse from(RefundEligibility eligibility) {
        return new RefundEligibilityResponse(eligibility.currency().name(), eligibility.unallocatedCredit().value(), eligibility.reservedByPendingRefunds().value(),
                eligibility.availableToRefund().value(), eligibility.hasOverdueDebt(), RefundBlockResponse.from(eligibility.blockedBy()),
                eligibility.payments().stream().map(RefundablePaymentResponse::from).toList());
    }
}
