package com.red_escuela.backend.notification.broadcast.lifecycle;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastEvent;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatusStateMachine;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.exception.IllegalBroadcastStatusTransitionException;
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

import static com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastEvent.*;
import static com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExhaustiveTransitionExpectations(machine = BroadcastStatusStateMachine.class)
class BroadcastStatusStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private static final Map<BroadcastStatus, Map<BroadcastEvent, BroadcastStatus>> ALLOWED = Map.of(
            DRAFT, Map.of(CONFIRM, CONFIRMED, CANCEL, CANCELLED),
            CONFIRMED, Map.of(START_SENDING, SENDING, CANCEL, CANCELLED),
            SENDING, Map.of(COMPLETE, COMPLETED, CANCEL, CANCELLED));

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (BroadcastEvent event : BroadcastEvent.values()) {
            expectations.add(TransitionExpectation.rejects(null, event, IllegalBroadcastStatusTransitionException.class));
        }
        for (BroadcastStatus from : BroadcastStatus.values()) {
            for (BroadcastEvent event : BroadcastEvent.values()) {
                BroadcastStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target)
                        : TransitionExpectation.rejects(from, event, IllegalBroadcastStatusTransitionException.class));
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
        BroadcastStatus from = (BroadcastStatus) expectation.fromState();
        BroadcastEvent event = (BroadcastEvent) expectation.event();
        if (expectation.isAllowed()) {
            assertThat(BroadcastStatusStateMachine.requireTargetStateFor(from, event)).isEqualTo(expectation.toState());
        } else {
            assertThatThrownBy(() -> BroadcastStatusStateMachine.requireTargetStateFor(from, event)).isInstanceOf(expectation.rejection());
        }
    }

    @Test
    void theTableListsOnlyTheSixAllowedTransitions() {
        assertThat(BroadcastStatusStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(6);
    }

    @Test
    void cancellingARejectedStatusLeavesTheBroadcastUntouched() {
        Broadcast broadcast = Broadcast.builder().status(COMPLETED).build();

        assertThatThrownBy(() -> broadcast.cancel(1L, "Motivo", NOW)).isInstanceOf(IllegalBroadcastStatusTransitionException.class);

        assertThat(broadcast.getStatus()).isEqualTo(COMPLETED);
        assertThat(broadcast.getCancelledAt()).isNull();
        assertThat(broadcast.getCancellationReason()).isNull();
    }

    @Test
    void confirmingADraftRecordsWhoAndWhen() {
        Broadcast broadcast = Broadcast.builder().status(DRAFT).build();

        broadcast.confirm(7L, NOW);

        assertThat(broadcast.getStatus()).isEqualTo(CONFIRMED);
        assertThat(broadcast.getConfirmedByPersonId()).isEqualTo(7L);
        assertThat(broadcast.getConfirmedAt()).isEqualTo(NOW);
    }
}
