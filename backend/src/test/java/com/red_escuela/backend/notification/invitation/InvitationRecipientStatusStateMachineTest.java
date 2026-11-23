package com.red_escuela.backend.notification.invitation;

import com.red_escuela.backend.notification.invitation.domain.InvitationErrorCode;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientEvent;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatusStateMachine;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.exception.InvitationRecipientDeliveryRequiredException;
import com.red_escuela.backend.notification.invitation.exception.InvitationRecipientInvalidTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.red_escuela.backend.notification.invitation.domain.InvitationRecipientEvent.*;
import static com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExhaustiveTransitionExpectations(machine = InvitationRecipientStatusStateMachine.class)
class InvitationRecipientStatusStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private static final Map<InvitationRecipientStatus, Map<InvitationRecipientEvent, InvitationRecipientStatus>> ALLOWED = Map.of(
            PENDING, Map.of(QUEUE, QUEUED, EXCLUDE, EXCLUDED, MARK_ACTIVATED, ACTIVATED),
            QUEUED, Map.of(MARK_SENT, SENT, MARK_FAILED, FAILED, EXCLUDE, EXCLUDED, MARK_ACTIVATED, ACTIVATED),
            SENT, Map.of(MARK_ACTIVATED, ACTIVATED),
            FAILED, Map.of(QUEUE, QUEUED, EXCLUDE, EXCLUDED, MARK_ACTIVATED, ACTIVATED));

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (InvitationRecipientEvent event : InvitationRecipientEvent.values()) {
            expectations.add(TransitionExpectation.rejects(null, event, InvitationRecipientInvalidTransitionException.class));
        }
        for (InvitationRecipientStatus from : InvitationRecipientStatus.values()) {
            for (InvitationRecipientEvent event : InvitationRecipientEvent.values()) {
                InvitationRecipientStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target)
                        : TransitionExpectation.rejects(from, event, InvitationRecipientInvalidTransitionException.class));
            }
        }
        return expectations;
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream().filter(expectation -> expectation.fromState() != null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        InvitationRecipientStatus from = (InvitationRecipientStatus) expectation.fromState();
        InvitationRecipientEvent event = (InvitationRecipientEvent) expectation.event();
        if (expectation.isAllowed()) {
            assertThat(InvitationRecipientStatusStateMachine.requireTargetStateFor(from, event)).isEqualTo(expectation.toState());
        } else {
            assertThatThrownBy(() -> InvitationRecipientStatusStateMachine.requireTargetStateFor(from, event)).isInstanceOf(expectation.rejection());
        }
    }

    @Test
    void theTableListsOnlyTheElevenAllowedTransitions() {
        assertThat(InvitationRecipientStatusStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(11);
    }

    @Test
    void aRecipientGoesThroughQueueFailRetryAndSend() {
        InvitationWaveRecipient recipient = InvitationWaveRecipient.pending(1L, 2L);

        recipient.queue(10L);
        recipient.markFailed(InvitationErrorCode.PROVIDER_REJECTED);
        assertThat(recipient.getStatus()).isEqualTo(FAILED);
        assertThat(recipient.getLastErrorCode()).isEqualTo(InvitationErrorCode.PROVIDER_REJECTED);
        recipient.queue(11L);
        recipient.markSent(NOW);

        assertThat(recipient.getStatus()).isEqualTo(SENT);
        assertThat(recipient.getAttempts()).isEqualTo(2);
        assertThat(recipient.getDeliveryId()).isEqualTo(11L);
        assertThat(recipient.getLastErrorCode()).isNull();
        assertThat(recipient.getSentAt()).isEqualTo(NOW);
    }

    @Test
    void aSentRecipientRejectsFurtherTransitionsAndStaysUntouched() {
        InvitationWaveRecipient recipient = InvitationWaveRecipient.pending(1L, 2L);
        recipient.queue(10L);
        recipient.markSent(NOW);

        assertThatThrownBy(() -> recipient.queue(12L)).isInstanceOf(InvitationRecipientInvalidTransitionException.class);
        assertThatThrownBy(() -> recipient.exclude(InvitationExclusionReason.NOT_ELIGIBLE)).isInstanceOf(InvitationRecipientInvalidTransitionException.class);

        assertThat(recipient.getStatus()).isEqualTo(SENT);
        assertThat(recipient.getDeliveryId()).isEqualTo(10L);
        assertThat(recipient.getAttempts()).isEqualTo(1);
        assertThat(recipient.getExclusionReason()).isNull();
    }

    @Test
    void aSentRecipientCanBeMarkedActivatedAndKeepsItsDelivery() {
        InvitationWaveRecipient recipient = InvitationWaveRecipient.pending(1L, 2L);
        recipient.queue(10L);
        recipient.markSent(NOW);

        recipient.markActivated();

        assertThat(recipient.getStatus()).isEqualTo(ACTIVATED);
        assertThat(recipient.getDeliveryId()).isEqualTo(10L);
        assertThat(recipient.getSentAt()).isEqualTo(NOW);
    }

    @Test
    void queueingWithoutADeliveryIsRejectedAndLeavesTheRecipientPending() {
        InvitationWaveRecipient recipient = InvitationWaveRecipient.pending(1L, 2L);

        assertThatThrownBy(() -> recipient.queue(null)).isInstanceOf(InvitationRecipientDeliveryRequiredException.class);

        assertThat(recipient.getStatus()).isEqualTo(PENDING);
        assertThat(recipient.getAttempts()).isZero();
    }

    @Test
    void aQueuedOrFailedRecipientCanBeExcludedForCancellationKeepingItsDelivery() {
        InvitationWaveRecipient queued = InvitationWaveRecipient.pending(1L, 2L);
        queued.queue(10L);
        InvitationWaveRecipient failed = InvitationWaveRecipient.pending(1L, 3L);
        failed.queue(11L);
        failed.markFailed(InvitationErrorCode.UNKNOWN);

        queued.exclude(InvitationExclusionReason.WAVE_CANCELLED);
        failed.exclude(InvitationExclusionReason.WAVE_CANCELLED);

        assertThat(queued.getStatus()).isEqualTo(EXCLUDED);
        assertThat(queued.getDeliveryId()).isEqualTo(10L);
        assertThat(failed.getStatus()).isEqualTo(EXCLUDED);
    }

    @Test
    void aPendingRecipientCanBeExcludedWithReasonOrMarkedActivated() {
        InvitationWaveRecipient excluded = InvitationWaveRecipient.pending(1L, 2L);
        InvitationWaveRecipient activated = InvitationWaveRecipient.pending(1L, 3L);

        excluded.exclude(InvitationExclusionReason.ALREADY_ACTIVE);
        activated.markActivated();

        assertThat(excluded.getStatus()).isEqualTo(EXCLUDED);
        assertThat(excluded.getExclusionReason()).isEqualTo(InvitationExclusionReason.ALREADY_ACTIVE);
        assertThat(activated.getStatus()).isEqualTo(ACTIVATED);
    }
}
