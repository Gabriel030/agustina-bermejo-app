package com.red_escuela.backend.notification.delivery.domain;

import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.notification.delivery.domain.DeliveryEvent.*;
import static com.red_escuela.backend.notification.delivery.domain.DeliveryStatus.*;

public final class DeliveryStatusStateMachine {

    public static final StateTransitionTable<DeliveryStatus, DeliveryEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(DeliveryStatus.class, DeliveryEvent.class)
                    .allowInitialTransition(QUEUE_FOR_DISPATCH, QUEUED)
                    .allowInitialTransition(DELIVER_IN_APP, SENT)
                    .allowInitialTransition(SUPPRESS, SUPPRESSED)
                    .allowTransition(QUEUED, SEND_VIA_PROVIDER, SENT)
                    .allowTransition(QUEUED, SEND_VIA_SIMULATOR, SENT_SIMULATED)
                    .allowTransition(QUEUED, SUPPRESS, SUPPRESSED)
                    .allowTransition(QUEUED, DEFER, DEFERRED)
                    .allowTransition(QUEUED, DISCARD, DISCARDED)
                    .allowTransition(QUEUED, GIVE_UP, FAILED)
                    .allowTransition(DEFERRED, SEND_VIA_PROVIDER, SENT)
                    .allowTransition(DEFERRED, SEND_VIA_SIMULATOR, SENT_SIMULATED)
                    .allowTransition(DEFERRED, SUPPRESS, SUPPRESSED)
                    .allowTransition(DEFERRED, DEFER, DEFERRED)
                    .allowTransition(DEFERRED, DISCARD, DISCARDED)
                    .allowTransition(DEFERRED, GIVE_UP, FAILED)
                    .buildTransitionTable();

    private DeliveryStatusStateMachine() {
    }

    public static Optional<DeliveryStatus> findTargetStateFor(DeliveryStatus from, DeliveryEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }
}
