package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.IllegalPaymentStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.payment.domain.PaymentEvent.*;
import static com.red_escuela.backend.billing.payment.domain.PaymentStatus.*;

public final class PaymentStatusStateMachine {

    public static final StateTransitionTable<PaymentStatus, PaymentEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(PaymentStatus.class, PaymentEvent.class)
                    .allowInitialTransition(CASH_PAYMENT_REGISTERED, APPROVED)
                    .allowInitialTransition(GATEWAY_PAYMENT_REGISTERED_AS_APPROVED, APPROVED)
                    .allowInitialTransition(PENDING_ONLINE_PAYMENT_CREATED, PENDING)
                    .allowInitialTransition(PENDING_BANK_TRANSFER_CREATED, PENDING)
                    .allowTransition(PENDING, PAYMENT_HOLD_AUTHORIZED, AUTHORIZED)
                    .allowTransition(PENDING, PAYMENT_APPROVED, APPROVED)
                    .allowTransition(AUTHORIZED, PAYMENT_APPROVED, APPROVED)
                    .allowTransition(PENDING, PAYMENT_REJECTED, REJECTED)
                    .allowTransition(PENDING, PENDING_PAYMENT_CANCELLED, CANCELLED)
                    .allowTransition(AUTHORIZED, PENDING_PAYMENT_CANCELLED, CANCELLED)
                    .allowTransition(APPROVED, PARTIAL_REFUND_REGISTERED, APPROVED)
                    .allowTransition(APPROVED, FULL_REFUND_REGISTERED, REFUNDED)
                    .allowTransition(APPROVED, CHARGEBACK_REGISTERED, CHARGED_BACK)
                    .allowTransition(CHARGED_BACK, CHARGEBACK_REVERSED, APPROVED)
                    .allowTransition(APPROVED, BANK_TRANSFER_APPROVAL_REVOKED, REJECTED)
                    .buildTransitionTable();

    private PaymentStatusStateMachine() {
    }

    public static PaymentStatus resolveNextStatus(PaymentStatus fromStatus, PaymentEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalPaymentStatusTransitionException(fromStatus, event));
    }
}
