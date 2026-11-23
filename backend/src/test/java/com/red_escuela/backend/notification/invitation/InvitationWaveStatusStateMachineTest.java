package com.red_escuela.backend.notification.invitation;

import com.red_escuela.backend.notification.invitation.domain.InvitationWaveEvent;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatusStateMachine;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveInvalidTransitionException;
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

import static com.red_escuela.backend.notification.invitation.domain.InvitationWaveEvent.*;
import static com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExhaustiveTransitionExpectations(machine = InvitationWaveStatusStateMachine.class)
class InvitationWaveStatusStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private static final Map<InvitationWaveStatus, Map<InvitationWaveEvent, InvitationWaveStatus>> ALLOWED = Map.of(
            DRAFT, Map.of(START, RUNNING, CANCEL, CANCELLED),
            RUNNING, Map.of(PAUSE, PAUSED, COMPLETE, COMPLETED, CANCEL, CANCELLED),
            PAUSED, Map.of(RESUME, RUNNING, CANCEL, CANCELLED));

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (InvitationWaveEvent event : InvitationWaveEvent.values()) {
            expectations.add(TransitionExpectation.rejects(null, event, InvitationWaveInvalidTransitionException.class));
        }
        for (InvitationWaveStatus from : InvitationWaveStatus.values()) {
            for (InvitationWaveEvent event : InvitationWaveEvent.values()) {
                InvitationWaveStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target)
                        : TransitionExpectation.rejects(from, event, InvitationWaveInvalidTransitionException.class));
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
        InvitationWaveStatus from = (InvitationWaveStatus) expectation.fromState();
        InvitationWaveEvent event = (InvitationWaveEvent) expectation.event();
        if (expectation.isAllowed()) {
            assertThat(InvitationWaveStatusStateMachine.requireTargetStateFor(from, event)).isEqualTo(expectation.toState());
        } else {
            assertThatThrownBy(() -> InvitationWaveStatusStateMachine.requireTargetStateFor(from, event)).isInstanceOf(expectation.rejection());
        }
    }

    @Test
    void theTableListsOnlyTheSevenAllowedTransitions() {
        assertThat(InvitationWaveStatusStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(7);
    }

    @Test
    void completedAndCancelledWavesAcceptNoEvent() {
        for (InvitationWaveEvent event : InvitationWaveEvent.values()) {
            assertThat(InvitationWaveStatusStateMachine.findTargetStateFor(COMPLETED, event)).isEmpty();
            assertThat(InvitationWaveStatusStateMachine.findTargetStateFor(CANCELLED, event)).isEmpty();
        }
    }

    @Test
    void startingADraftRecordsTheStartTimestamp() {
        InvitationWave wave = InvitationWave.builder().status(DRAFT).build();

        wave.start(NOW);

        assertThat(wave.getStatus()).isEqualTo(RUNNING);
        assertThat(wave.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void pausingARunningWaveRecordsThePauseTimestampAndResumingKeepsIt() {
        InvitationWave wave = InvitationWave.builder().status(RUNNING).build();

        wave.pause(NOW);
        wave.resume();

        assertThat(wave.getStatus()).isEqualTo(RUNNING);
        assertThat(wave.getPausedAt()).isEqualTo(NOW);
    }

    @Test
    void cancellingARejectedStatusLeavesTheWaveUntouched() {
        InvitationWave wave = InvitationWave.builder().status(COMPLETED).build();

        assertThatThrownBy(() -> wave.cancel(NOW)).isInstanceOf(InvitationWaveInvalidTransitionException.class);

        assertThat(wave.getStatus()).isEqualTo(COMPLETED);
        assertThat(wave.getCancelledAt()).isNull();
    }

    @Test
    void completingARunningWaveRecordsTheCompletionTimestamp() {
        InvitationWave wave = InvitationWave.builder().status(RUNNING).build();

        wave.complete(NOW);

        assertThat(wave.getStatus()).isEqualTo(COMPLETED);
        assertThat(wave.getCompletedAt()).isEqualTo(NOW);
    }
}
