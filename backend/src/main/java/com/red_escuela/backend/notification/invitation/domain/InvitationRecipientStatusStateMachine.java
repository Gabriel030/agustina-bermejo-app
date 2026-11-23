package com.red_escuela.backend.notification.invitation.domain;

import com.red_escuela.backend.notification.invitation.exception.InvitationRecipientInvalidTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.notification.invitation.domain.InvitationRecipientEvent.*;
import static com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus.*;

public final class InvitationRecipientStatusStateMachine {

    public static final StateTransitionTable<InvitationRecipientStatus, InvitationRecipientEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(InvitationRecipientStatus.class, InvitationRecipientEvent.class)
                    .allowTransition(PENDING, QUEUE, QUEUED)
                    .allowTransition(PENDING, EXCLUDE, EXCLUDED)
                    .allowTransition(PENDING, MARK_ACTIVATED, ACTIVATED)
                    .allowTransition(QUEUED, MARK_SENT, SENT)
                    .allowTransition(QUEUED, MARK_FAILED, FAILED)
                    .allowTransition(FAILED, QUEUE, QUEUED)
                    .buildTransitionTable();

    private InvitationRecipientStatusStateMachine() {
    }

    public static Optional<InvitationRecipientStatus> findTargetStateFor(InvitationRecipientStatus from, InvitationRecipientEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }

    public static InvitationRecipientStatus requireTargetStateFor(InvitationRecipientStatus from, InvitationRecipientEvent event) {
        return findTargetStateFor(from, event).orElseThrow(() -> new InvitationRecipientInvalidTransitionException(from, event));
    }
}
