package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.IllegalDiscountDefinitionTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.billing.discount.domain.DiscountDefinitionEvent.*;
import static com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus.*;

public final class DiscountDefinitionStateMachine {

    public static final StateTransitionTable<DiscountDefinitionStatus, DiscountDefinitionEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(DiscountDefinitionStatus.class, DiscountDefinitionEvent.class)
                    .allowInitialTransition(DEFINITION_CREATED, ACTIVE)
                    .allowTransition(ACTIVE, DEFINITION_CLOSED, CLOSED)
                    .buildTransitionTable();

    private DiscountDefinitionStateMachine() {
    }

    public static void requireClosableFrom(DiscountDefinitionStatus from) {
        if (findTargetStateFor(from, DEFINITION_CLOSED).isEmpty()) {
            throw new IllegalDiscountDefinitionTransitionException(from, DEFINITION_CLOSED);
        }
    }

    public static Optional<DiscountDefinitionStatus> findTargetStateFor(DiscountDefinitionStatus from, DiscountDefinitionEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }
}
