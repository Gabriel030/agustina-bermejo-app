package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.run.exception.IllegalBillingRunTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.run.domain.BillingRunEvent.*;
import static com.red_escuela.backend.billing.run.domain.BillingRunStatus.*;

public final class BillingRunStateMachine {

    public static final StateTransitionTable<BillingRunStatus, BillingRunEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(BillingRunStatus.class, BillingRunEvent.class)
                    .allowInitialTransition(STEP_STARTED, RUNNING)
                    .allowInitialTransition(STEP_DECLARED_NOT_APPLICABLE, NOT_APPLICABLE)
                    .allowTransition(RUNNING, STEP_FINISHED_CLEAN, COMPLETED)
                    .allowTransition(RUNNING, STEP_FINISHED_WITH_ERRORS, COMPLETED_WITH_ERRORS)
                    .allowTransition(RUNNING, STEP_ABORTED, FAILED)
                    .buildTransitionTable();

    private BillingRunStateMachine() {
    }

    public static BillingRunStatus resolveNextStatus(BillingRunStatus fromStatus, BillingRunEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalBillingRunTransitionException(String.valueOf(fromStatus), event.name()));
    }
}
