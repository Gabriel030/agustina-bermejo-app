package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.collections.exception.IllegalEnrollmentPaymentStatusTransitionException;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentEvent.*;
import static com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.*;

public final class EnrollmentPaymentStatusStateMachine {

    public static final StateTransitionTable<EnrollmentPaymentStatus, EnrollmentPaymentEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(EnrollmentPaymentStatus.class, EnrollmentPaymentEvent.class)
                    .allowInitialTransition(STANDING_OPENED, UP_TO_DATE)
                    .allowTransition(UP_TO_DATE, OVERDUE_DEBT_DETECTED, OVERDUE)
                    .allowTransition(OVERDUE, OVERDUE_DEBT_CLEARED, UP_TO_DATE)
                    .allowTransition(OVERDUE, MONTH_CLOSED_WITH_UNPAID_INSTALLMENT, SUSPENDED)
                    .allowTransition(SUSPENDED, ACCOUNT_SETTLED, UP_TO_DATE)
                    .allowTransition(SUSPENDED, TERM_CLOSED_WHILE_SUSPENDED, WITHDRAWN_FOR_NONPAYMENT)
                    .allowTransition(SUSPENDED, COURSE_ENDED_WHILE_SUSPENDED, WITHDRAWN_FOR_NONPAYMENT)
                    .buildTransitionTable();

    private EnrollmentPaymentStatusStateMachine() {
    }

    public static EnrollmentPaymentStatus resolveNextStatus(EnrollmentPaymentStatus fromStatus, EnrollmentPaymentEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalEnrollmentPaymentStatusTransitionException(fromStatus, event));
    }
}
