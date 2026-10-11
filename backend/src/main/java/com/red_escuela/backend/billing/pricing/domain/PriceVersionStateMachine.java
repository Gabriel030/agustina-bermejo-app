package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.billing.pricing.domain.PriceVersionEvent.*;
import static com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus.*;

public final class PriceVersionStateMachine {

    public static final StateTransitionTable<PriceVersionStatus, PriceVersionEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(PriceVersionStatus.class, PriceVersionEvent.class)
                    .allowInitialTransition(PROPOSE, PROPOSED)
                    .allowInitialTransition(PUBLISH_FOR_FUTURE, SCHEDULED)
                    .allowInitialTransition(PUBLISH_FOR_TODAY, EFFECTIVE)
                    .allowTransition(PROPOSED, APPROVE_FOR_FUTURE, SCHEDULED)
                    .allowTransition(PROPOSED, APPROVE_FOR_TODAY, EFFECTIVE)
                    .allowTransition(PROPOSED, REJECT, REJECTED)
                    .allowTransition(PROPOSED, WITHDRAW, WITHDRAWN)
                    .allowTransition(PROPOSED, EXPIRE, EXPIRED)
                    .allowTransition(SCHEDULED, REACH_START, EFFECTIVE)
                    .allowTransition(SCHEDULED, CANCEL, CANCELLED)
                    .allowTransition(EFFECTIVE, SUPERSEDE, SUPERSEDED)
                    .buildTransitionTable();

    private PriceVersionStateMachine() {
    }

    public static Optional<PriceVersionStatus> findTargetStateFor(PriceVersionStatus from, PriceVersionEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }
}
