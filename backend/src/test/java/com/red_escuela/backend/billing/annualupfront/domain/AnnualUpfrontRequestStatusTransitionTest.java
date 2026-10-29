package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.billing.annualupfront.exception.IllegalAnnualUpfrontRequestTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestEvent.*;
import static com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExhaustiveTransitionExpectations(machine = AnnualUpfrontRequestStateMachine.class)
class AnnualUpfrontRequestStatusTransitionTest {
    private static final Class<IllegalAnnualUpfrontRequestTransitionException> ILLEGAL = IllegalAnnualUpfrontRequestTransitionException.class;

    private static final Map<AnnualUpfrontRequestStatus, Map<AnnualUpfrontRequestEvent, AnnualUpfrontRequestStatus>> ALLOWED = Map.of(
            AWAITING_PAYMENT, Map.of(PAYMENT_INITIATED, PAYMENT_UNDER_REVIEW, DEADLINE_PASSED, DISCARDED, WITHDRAWN, DISCARDED, CANCELLED_BY_STUDENT, DISCARDED),
            PAYMENT_UNDER_REVIEW, Map.of(PAYMENT_APPROVED, CONFIRMED, PAYMENT_REJECTED, DISCARDED));

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (AnnualUpfrontRequestEvent event : AnnualUpfrontRequestEvent.values()) {
            expectations.add(event == REQUESTED ? TransitionExpectation.allows(null, event, AWAITING_PAYMENT) : TransitionExpectation.rejects(null, event, ILLEGAL));
        }
        for (AnnualUpfrontRequestStatus from : AnnualUpfrontRequestStatus.values()) {
            for (AnnualUpfrontRequestEvent event : AnnualUpfrontRequestEvent.values()) {
                AnnualUpfrontRequestStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target) : TransitionExpectation.rejects(from, event, ILLEGAL));
            }
        }
        return expectations;
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        AnnualUpfrontRequestStatus from = (AnnualUpfrontRequestStatus) expectation.fromState();
        AnnualUpfrontRequestEvent event = (AnnualUpfrontRequestEvent) expectation.event();
        if (expectation.isAllowed()) {
            assertThat(AnnualUpfrontRequestStateMachine.requireTargetFor(from, event)).isEqualTo(expectation.toState());
        } else {
            assertThatThrownBy(() -> AnnualUpfrontRequestStateMachine.requireTargetFor(from, event)).isInstanceOf(expectation.rejection());
        }
    }

    @Test
    void theTableListsOnlyTheSevenAllowedTransitions() {
        assertThat(AnnualUpfrontRequestStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(7);
    }
}
