package com.red_escuela.backend.graduation.certificate.regular.entity;

import com.red_escuela.backend.graduation.certificate.regular.exception.IllegalCertificateExceptionTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionEvent.*;
import static com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExhaustiveTransitionExpectations(machine = CertificateExceptionStateMachine.class)
class CertificateExceptionStateMachineTest {

    private static final Class<IllegalCertificateExceptionTransitionException> ILLEGAL = IllegalCertificateExceptionTransitionException.class;

    private static final Map<CertificateExceptionStatus, Map<CertificateExceptionEvent, CertificateExceptionStatus>> ALLOWED = Map.of(
            PENDING, Map.of(APPROVE, APPROVED, REJECT, REJECTED, WITHDRAW, WITHDRAWN),
            APPROVED, Map.of(CONSUME, CONSUMED, EXPIRE, EXPIRED));

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (CertificateExceptionEvent event : CertificateExceptionEvent.values()) {
            if (event == REQUEST) {
                expectations.add(TransitionExpectation.allows(null, event, PENDING));
            } else if (event == DIRECT_APPROVE) {
                expectations.add(TransitionExpectation.allows(null, event, APPROVED));
            } else {
                expectations.add(TransitionExpectation.rejects(null, event, ILLEGAL));
            }
        }
        for (CertificateExceptionStatus from : CertificateExceptionStatus.values()) {
            for (CertificateExceptionEvent event : CertificateExceptionEvent.values()) {
                CertificateExceptionStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
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
        CertificateExceptionStatus from = (CertificateExceptionStatus) expectation.fromState();
        CertificateExceptionEvent event = (CertificateExceptionEvent) expectation.event();
        if (expectation.isAllowed()) {
            assertThat(CertificateExceptionStateMachine.requireTargetFor(from, event)).isEqualTo(expectation.toState());
        } else {
            assertThatThrownBy(() -> CertificateExceptionStateMachine.requireTargetFor(from, event)).isInstanceOf(expectation.rejection());
        }
    }

    @Test
    void theTableListsOnlyTheSevenAllowedTransitions() {
        assertThat(CertificateExceptionStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(7);
    }
}
