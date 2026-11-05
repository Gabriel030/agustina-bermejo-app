package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.run.exception.IllegalBillingRunTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemEvent.*;
import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult.*;

public final class MonthlyInvoiceRunItemStateMachine {

    public static final StateTransitionTable<MonthlyInvoiceRunItemResult, MonthlyInvoiceRunItemEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(MonthlyInvoiceRunItemResult.class, MonthlyInvoiceRunItemEvent.class)
                    .allowTransition(FAILED, RETRY_ISSUED, ISSUED)
                    .allowTransition(FAILED, RETRY_ALREADY_ISSUED, ALREADY_ISSUED)
                    .allowTransition(FAILED, RETRY_NOT_APPLICABLE, NOT_APPLICABLE)
                    .allowTransition(FAILED, RETRY_FAILED_WITH_WAIT, FAILED)
                    .allowTransition(FAILED, RETRY_FAILED_WITHOUT_WAIT, RETRIES_EXHAUSTED)
                    .allowTransition(RETRIES_EXHAUSTED, RETRY_ISSUED, ISSUED)
                    .allowTransition(RETRIES_EXHAUSTED, RETRY_ALREADY_ISSUED, ALREADY_ISSUED)
                    .allowTransition(RETRIES_EXHAUSTED, RETRY_NOT_APPLICABLE, NOT_APPLICABLE)
                    .allowTransition(RETRIES_EXHAUSTED, RETRY_FAILED_WITH_WAIT, FAILED)
                    .allowTransition(RETRIES_EXHAUSTED, RETRY_FAILED_WITHOUT_WAIT, RETRIES_EXHAUSTED)
                    .buildTransitionTable();

    private MonthlyInvoiceRunItemStateMachine() {
    }

    public static MonthlyInvoiceRunItemResult resolveNextResult(MonthlyInvoiceRunItemResult fromResult, MonthlyInvoiceRunItemEvent event) {
        return TRANSITIONS.findTargetStateFor(fromResult, event)
                .orElseThrow(() -> new IllegalBillingRunTransitionException(String.valueOf(fromResult), event.name()));
    }
}
