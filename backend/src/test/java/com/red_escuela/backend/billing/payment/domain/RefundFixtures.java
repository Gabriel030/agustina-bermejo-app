package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;

public final class RefundFixtures {

    public static final StatusChangeActor ACTOR = StatusChangeActor.ofPerson(7L);
    public static final LocalDate REQUESTED_ON = LocalDate.of(2027, 4, 1);
    public static final RefundDestination DESTINATION = new RefundDestination("0110000000000000000001", "Ana Pérez");

    private RefundFixtures() {
    }

    public static RefundPart part(long paymentId, PaymentMethodType method, String amount) {
        return new RefundPart(paymentId, method, PaymentFixtures.ars(amount), DESTINATION);
    }

    public static Refund requested() {
        return Refund.requestPartOf(1L, 10L, part(5L, PaymentMethodType.BANK_TRANSFER, "1000.00"), 7L, 99L, REQUESTED_ON);
    }

    public static Refund in(RefundStatus status) {
        Refund refund = requested();
        switch (status) {
            case REQUESTED -> {
            }
            case APPROVED -> refund.approve(REQUESTED_ON, ACTOR);
            case REJECTED -> refund.reject("no", ACTOR);
            case COMPLETED -> {
                refund.approve(REQUESTED_ON, ACTOR);
                refund.complete(REQUESTED_ON, "REF-1", ACTOR);
            }
            case FAILED -> {
                refund.approve(REQUESTED_ON, ACTOR);
                refund.fail("banco", ACTOR);
            }
        }
        return refund;
    }

    public static Payment approvedCash(String amount) {
        return Payment.registerApprovedCashPayment(PaymentFixtures.arsAccount(), PaymentFixtures.ars(amount), PaymentFixtures.PAID_ON, new CashPaymentDetails("R-1"),
                "key-" + System.nanoTime(), PaymentFixtures.ACTOR);
    }
}
