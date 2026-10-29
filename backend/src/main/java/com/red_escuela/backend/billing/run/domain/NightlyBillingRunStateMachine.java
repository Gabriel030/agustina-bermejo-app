package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.run.exception.IllegalBillingRunTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.run.domain.NightlyBillingRunEvent.*;
import static com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus.*;

public final class NightlyBillingRunStateMachine {

    public static final StateTransitionTable<NightlyBillingRunStatus, NightlyBillingRunEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(NightlyBillingRunStatus.class, NightlyBillingRunEvent.class)
                    .allowInitialTransition(NIGHT_STARTED, RUNNING)
                    .allowTransition(RUNNING, ALL_STEPS_FINISHED_CLEAN, COMPLETED)
                    .allowTransition(RUNNING, ALL_STEPS_FINISHED_WITH_ERRORS, COMPLETED_WITH_ERRORS)
                    .allowTransition(RUNNING, STEP_ABORTED, FAILED)
                    .buildTransitionTable();

    private NightlyBillingRunStateMachine() {
    }

    public static NightlyBillingRunStatus resolveNextStatus(NightlyBillingRunStatus fromStatus, NightlyBillingRunEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalBillingRunTransitionException(String.valueOf(fromStatus), event.name()));
    }
}
