package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.run.exception.IllegalBillingRunTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent.*;
import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus.*;

public final class MonthlyInvoiceRunStateMachine {

    public static final StateTransitionTable<MonthlyInvoiceRunStatus, MonthlyInvoiceRunEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(MonthlyInvoiceRunStatus.class, MonthlyInvoiceRunEvent.class)
                    .allowInitialTransition(RUN_STARTED, RUNNING)
                    .allowInitialTransition(RUN_BLOCKED_BY_NIGHT, FAILED)
                    .allowTransition(RUNNING, RUN_FINISHED_CLEAN, COMPLETED)
                    .allowTransition(RUNNING, RUN_FINISHED_WITH_FAILURES, COMPLETED_WITH_FAILURES)
                    .allowTransition(RUNNING, RUN_FINISHED_WITH_DRAFTS_PENDING_REVIEW, AWAITING_REVIEW)
                    .allowTransition(AWAITING_REVIEW, RUN_REVIEW_COMPLETED, COMPLETED)
                    .allowTransition(RUNNING, RUN_ABORTED, FAILED)
                    .buildTransitionTable();

    private MonthlyInvoiceRunStateMachine() {
    }

    public static MonthlyInvoiceRunStatus resolveNextStatus(MonthlyInvoiceRunStatus fromStatus, MonthlyInvoiceRunEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalBillingRunTransitionException(String.valueOf(fromStatus), event.name()));
    }
}
