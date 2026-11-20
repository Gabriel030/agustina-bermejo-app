package com.red_escuela.backend.graduation.certificate.regular.entity;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionEvent.*;
import static com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class CertificateExceptionStateMachineTest {

    private static final Map<CertificateExceptionStatus, Map<CertificateExceptionEvent, CertificateExceptionStatus>> VALID = Map.of(
            PENDING, Map.of(APPROVE, APPROVED, REJECT, REJECTED, WITHDRAW, WITHDRAWN),
            APPROVED, Map.of(CONSUME, CONSUMED, EXPIRE, EXPIRED));

    @Test
    void everyValidTransitionLandsOnTheExpectedState() {
        VALID.forEach((from, byEvent) -> byEvent.forEach((event, target) ->
                assertThat(CertificateExceptionStateMachine.findTargetStateFor(from, event)).as(from + " + " + event).contains(target)));
    }

    @Test
    void everyOtherStateAndEventPairIsRejected() {
        for (CertificateExceptionStatus from : CertificateExceptionStatus.values()) {
            for (CertificateExceptionEvent event : CertificateExceptionEvent.values()) {
                if (!VALID.getOrDefault(from, Map.of()).containsKey(event)) {
                    assertThat(CertificateExceptionStateMachine.findTargetStateFor(from, event)).as(from + " + " + event).isEmpty();
                }
            }
        }
    }

    @Test
    void onlyRequestCanStartTheLifecycle() {
        for (CertificateExceptionEvent event : CertificateExceptionEvent.values()) {
            Optional<CertificateExceptionStatus> target = CertificateExceptionStateMachine.findTargetStateFor(null, event);
            assertThat(target).as("initial + " + event).isEqualTo(event == REQUEST ? Optional.of(PENDING) : Optional.empty());
        }
    }

    @Test
    void terminalStatesAllowNoTransitions() {
        for (CertificateExceptionStatus terminal : Set.of(REJECTED, WITHDRAWN, CONSUMED, EXPIRED)) {
            for (CertificateExceptionEvent event : CertificateExceptionEvent.values()) {
                assertThat(CertificateExceptionStateMachine.findTargetStateFor(terminal, event)).as(terminal + " + " + event).isEmpty();
            }
        }
    }
}
