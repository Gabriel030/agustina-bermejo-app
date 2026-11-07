package com.red_escuela.backend.billing.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.payment.domain.CashPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.Chargeback;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.StudentPaymentOverview;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StudentPaymentResponse(Long id, Money amount, @Schema(nullable = true) Money approvedAmount, Money refundedAmount, String status, String gateway, String methodType,
                                     LocalDate paidOn, LocalDateTime registeredAt, @Schema(nullable = true) String receiptNumber, @Schema(nullable = true) ChargebackSummaryResponse chargeback,
                                     @Schema(nullable = true) PendingRefundsSummaryResponse pendingRefunds, @Schema(nullable = true) PaymentActionsResponse actions) {

    public record ChargebackSummaryResponse(Long chargebackId, Money amount, LocalDate chargedBackOn, @Schema(nullable = true) String reference, @Schema(nullable = true) LocalDate reversedOn) {

        static ChargebackSummaryResponse from(Chargeback chargeback) {
            return chargeback == null ? null : new ChargebackSummaryResponse(chargeback.getId(), chargeback.getAmount().value(), chargeback.getChargedBackOn(),
                    chargeback.getGatewayReference(), chargeback.getReversedOn());
        }
    }

    public record PendingRefundsSummaryResponse(int count, Money amount, List<PendingRefundResponse> parts) {
    }

    public record PaymentActionsResponse(PaymentActionAvailabilityResponse registerChargeback, PaymentActionAvailabilityResponse reverseChargeback,
                                         PaymentActionAvailabilityResponse revokeApproval) {
    }

    public static StudentPaymentResponse from(Payment payment) {
        return of(payment, null, null, null);
    }

    public static StudentPaymentResponse from(StudentPaymentOverview overview) {
        Payment payment = overview.payment();
        List<PendingRefundResponse> parts = overview.pendingRefunds().stream().map(PendingRefundResponse::from).toList();
        Money pendingAmount = parts.stream().map(PendingRefundResponse::amount).reduce(Money.zero(payment.getCurrency()), Money::plus);
        return of(payment, ChargebackSummaryResponse.from(overview.latestChargeback()), new PendingRefundsSummaryResponse(parts.size(), pendingAmount, parts),
                new PaymentActionsResponse(PaymentActionAvailabilityResponse.from(overview.chargebackRegistration()),
                        PaymentActionAvailabilityResponse.from(overview.chargebackReversal()), PaymentActionAvailabilityResponse.from(overview.approvalRevocation())));
    }

    private static StudentPaymentResponse of(Payment payment, ChargebackSummaryResponse chargeback, PendingRefundsSummaryResponse pendingRefunds,
                                             PaymentActionsResponse actions) {
        String receiptNumber = payment.getMethodDetails() instanceof CashPaymentDetails cash ? cash.receiptNumber() : null;
        return new StudentPaymentResponse(payment.getId(), payment.getAmount().value(),
                payment.getApprovedAmount() == null ? null : payment.getApprovedAmount().value(), payment.getRefundedAmount().value(),
                payment.getStatus().name(), payment.getGateway().name(), payment.getMethodType().name(), payment.getPaidOn(), payment.getRegisteredAt(),
                receiptNumber, chargeback, pendingRefunds, actions);
    }
}
