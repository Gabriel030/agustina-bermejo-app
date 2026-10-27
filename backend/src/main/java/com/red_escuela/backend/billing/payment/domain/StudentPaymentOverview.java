package com.red_escuela.backend.billing.payment.domain;

import java.util.List;

public record StudentPaymentOverview(Payment payment, Chargeback latestChargeback, List<PendingRefundOfPayment> pendingRefunds,
                                     PaymentActionAvailability chargebackRegistration, PaymentActionAvailability chargebackReversal,
                                     PaymentActionAvailability approvalRevocation) {

    public StudentPaymentOverview {
        pendingRefunds = List.copyOf(pendingRefunds);
    }
}
