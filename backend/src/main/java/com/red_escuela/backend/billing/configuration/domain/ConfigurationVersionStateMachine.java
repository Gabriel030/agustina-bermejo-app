package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionEvent.*;
import static com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus.*;

public final class ConfigurationVersionStateMachine {

    public static final StateTransitionTable<ConfigurationVersionStatus, ConfigurationVersionEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(ConfigurationVersionStatus.class, ConfigurationVersionEvent.class)
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

    private ConfigurationVersionStateMachine() {
    }

    public static Optional<ConfigurationVersionStatus> findTargetStateFor(ConfigurationVersionStatus from, ConfigurationVersionEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }
}
