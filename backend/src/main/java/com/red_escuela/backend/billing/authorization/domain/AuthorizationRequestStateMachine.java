package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.APPROVE;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.EXPIRE;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.OPEN;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.REJECT;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.WITHDRAW;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.APPROVED;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.EXPIRED;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.PENDING;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.REJECTED;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.WITHDRAWN;

public final class AuthorizationRequestStateMachine {

    public static final StateTransitionTable<AuthorizationRequestStatus, AuthorizationRequestEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(AuthorizationRequestStatus.class, AuthorizationRequestEvent.class)
                    .allowInitialTransition(OPEN, PENDING)
                    .allowTransition(PENDING, APPROVE, APPROVED)
                    .allowTransition(PENDING, REJECT, REJECTED)
                    .allowTransition(PENDING, WITHDRAW, WITHDRAWN)
                    .allowTransition(PENDING, EXPIRE, EXPIRED)
                    .buildTransitionTable();

    private AuthorizationRequestStateMachine() {
    }

    public static Optional<AuthorizationRequestStatus> findTargetStateFor(AuthorizationRequestStatus from, AuthorizationRequestEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }
}
