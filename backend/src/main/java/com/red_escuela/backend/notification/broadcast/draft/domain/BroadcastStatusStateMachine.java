package com.red_escuela.backend.notification.broadcast.draft.domain;

import com.red_escuela.backend.notification.broadcast.draft.exception.IllegalBroadcastStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastEvent.*;
import static com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus.*;

public final class BroadcastStatusStateMachine {

    public static final StateTransitionTable<BroadcastStatus, BroadcastEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(BroadcastStatus.class, BroadcastEvent.class)
                    .allowTransition(DRAFT, CONFIRM, CONFIRMED)
                    .allowTransition(DRAFT, CANCEL, CANCELLED)
                    .allowTransition(CONFIRMED, START_SENDING, SENDING)
                    .allowTransition(CONFIRMED, CANCEL, CANCELLED)
                    .allowTransition(SENDING, COMPLETE, COMPLETED)
                    .allowTransition(SENDING, CANCEL, CANCELLED)
                    .buildTransitionTable();

    private BroadcastStatusStateMachine() {
    }

    public static Optional<BroadcastStatus> findTargetStateFor(BroadcastStatus from, BroadcastEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }

    public static BroadcastStatus requireTargetStateFor(BroadcastStatus from, BroadcastEvent event) {
        return findTargetStateFor(from, event).orElseThrow(() -> new IllegalBroadcastStatusTransitionException(from, event));
    }
}
