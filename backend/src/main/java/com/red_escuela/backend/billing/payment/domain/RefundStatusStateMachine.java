package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.IllegalRefundStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.payment.domain.RefundEvent.*;
import static com.red_escuela.backend.billing.payment.domain.RefundStatus.*;

public final class RefundStatusStateMachine {

    public static final StateTransitionTable<RefundStatus, RefundEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(RefundStatus.class, RefundEvent.class)
                    .allowInitialTransition(REFUND_REQUESTED, REQUESTED)
                    .allowTransition(REQUESTED, REFUND_APPROVED, APPROVED)
                    .allowTransition(REQUESTED, REFUND_REJECTED, REJECTED)
                    .allowTransition(APPROVED, REFUND_COMPLETED, COMPLETED)
                    .allowTransition(APPROVED, REFUND_FAILED, FAILED)
                    .buildTransitionTable();

    private RefundStatusStateMachine() {
    }

    public static RefundStatus resolveNextStatus(RefundStatus fromStatus, RefundEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalRefundStatusTransitionException(fromStatus, event));
    }
}
