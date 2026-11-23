package com.red_escuela.backend.notification.invitation.domain;

import com.red_escuela.backend.notification.invitation.exception.InvitationWaveInvalidTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.notification.invitation.domain.InvitationWaveEvent.*;
import static com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus.*;

public final class InvitationWaveStatusStateMachine {

    public static final StateTransitionTable<InvitationWaveStatus, InvitationWaveEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(InvitationWaveStatus.class, InvitationWaveEvent.class)
                    .allowTransition(DRAFT, START, RUNNING)
                    .allowTransition(DRAFT, CANCEL, CANCELLED)
                    .allowTransition(RUNNING, PAUSE, PAUSED)
                    .allowTransition(RUNNING, COMPLETE, COMPLETED)
                    .allowTransition(RUNNING, CANCEL, CANCELLED)
                    .allowTransition(PAUSED, RESUME, RUNNING)
                    .allowTransition(PAUSED, CANCEL, CANCELLED)
                    .buildTransitionTable();

    private InvitationWaveStatusStateMachine() {
    }

    public static Optional<InvitationWaveStatus> findTargetStateFor(InvitationWaveStatus from, InvitationWaveEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }

    public static InvitationWaveStatus requireTargetStateFor(InvitationWaveStatus from, InvitationWaveEvent event) {
        return findTargetStateFor(from, event).orElseThrow(() -> new InvitationWaveInvalidTransitionException(from, event));
    }
}
