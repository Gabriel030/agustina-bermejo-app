package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.billing.annualupfront.exception.IllegalAnnualUpfrontRequestTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestEvent.*;
import static com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus.*;

public final class AnnualUpfrontRequestStateMachine {

    public static final StateTransitionTable<AnnualUpfrontRequestStatus, AnnualUpfrontRequestEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(AnnualUpfrontRequestStatus.class, AnnualUpfrontRequestEvent.class)
                    .allowInitialTransition(REQUESTED, AWAITING_PAYMENT)
                    .allowTransition(AWAITING_PAYMENT, PAYMENT_INITIATED, PAYMENT_UNDER_REVIEW)
                    .allowTransition(PAYMENT_UNDER_REVIEW, PAYMENT_APPROVED, CONFIRMED)
                    .allowTransition(PAYMENT_UNDER_REVIEW, PAYMENT_REJECTED, DISCARDED)
                    .allowTransition(AWAITING_PAYMENT, DEADLINE_PASSED, DISCARDED)
                    .allowTransition(AWAITING_PAYMENT, WITHDRAWN, DISCARDED)
                    .allowTransition(AWAITING_PAYMENT, CANCELLED_BY_STUDENT, DISCARDED)
                    .buildTransitionTable();

    private AnnualUpfrontRequestStateMachine() {
    }

    public static AnnualUpfrontRequestStatus requireTargetFor(AnnualUpfrontRequestStatus from, AnnualUpfrontRequestEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event).orElseThrow(() -> new IllegalAnnualUpfrontRequestTransitionException(from, event));
    }
}
