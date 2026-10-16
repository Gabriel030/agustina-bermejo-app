package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.IllegalStudentDiscountTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.billing.discount.domain.StudentDiscountEvent.*;
import static com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus.*;

public final class StudentDiscountStateMachine {

    public static final StateTransitionTable<StudentDiscountStatus, StudentDiscountEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(StudentDiscountStatus.class, StudentDiscountEvent.class)
                    .allowInitialTransition(DISCOUNT_GRANTED, ACTIVE)
                    .allowTransition(ACTIVE, LAST_INSTALLMENT_CONSUMED, EXHAUSTED)
                    .allowTransition(ACTIVE, CONSUMPTION_RETURNED, ACTIVE)
                    .allowTransition(EXHAUSTED, CONSUMPTION_RETURNED, ACTIVE)
                    .allowTransition(ACTIVE, DISCOUNT_REVOKED, REVOKED)
                    .allowTransition(ACTIVE, ENROLLMENT_ENDED, ENDED)
                    .buildTransitionTable();

    private StudentDiscountStateMachine() {
    }

    public static StudentDiscountStatus requireTargetFor(StudentDiscountStatus from, StudentDiscountEvent event) {
        return findTargetStateFor(from, event).orElseThrow(() -> new IllegalStudentDiscountTransitionException(from, event));
    }

    public static Optional<StudentDiscountStatus> findTargetStateFor(StudentDiscountStatus from, StudentDiscountEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }
}
