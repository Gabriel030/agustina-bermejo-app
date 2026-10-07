package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.APPROVE;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.EXPIRE;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.OPEN;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.REJECT;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestEvent.WITHDRAW;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.APPROVED;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.EXPIRED;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.PENDING;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.REJECTED;
import static com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus.WITHDRAWN;
import static com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation.allows;
import static com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation.rejects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-176")
@Tag("D-180")
@ExhaustiveTransitionExpectations(machine = AuthorizationRequestStateMachine.class)
class AuthorizationRequestStateMachineTest {
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Class<IllegalAuthorizationRequestTransitionException> ILLEGAL = IllegalAuthorizationRequestTransitionException.class;

    static List<TransitionExpectation> expectedTransitions() {
        return List.of(
                allows(null, OPEN, PENDING),
                rejects(null, APPROVE, ILLEGAL),
                rejects(null, REJECT, ILLEGAL),
                rejects(null, WITHDRAW, ILLEGAL),
                rejects(null, EXPIRE, ILLEGAL),
                rejects(PENDING, OPEN, ILLEGAL),
                allows(PENDING, APPROVE, APPROVED),
                allows(PENDING, REJECT, REJECTED),
                allows(PENDING, WITHDRAW, WITHDRAWN),
                allows(PENDING, EXPIRE, EXPIRED),
                rejects(APPROVED, OPEN, ILLEGAL),
                rejects(APPROVED, APPROVE, ILLEGAL),
                rejects(APPROVED, REJECT, ILLEGAL),
                rejects(APPROVED, WITHDRAW, ILLEGAL),
                rejects(APPROVED, EXPIRE, ILLEGAL),
                rejects(REJECTED, OPEN, ILLEGAL),
                rejects(REJECTED, APPROVE, ILLEGAL),
                rejects(REJECTED, REJECT, ILLEGAL),
                rejects(REJECTED, WITHDRAW, ILLEGAL),
                rejects(REJECTED, EXPIRE, ILLEGAL),
                rejects(WITHDRAWN, OPEN, ILLEGAL),
                rejects(WITHDRAWN, APPROVE, ILLEGAL),
                rejects(WITHDRAWN, REJECT, ILLEGAL),
                rejects(WITHDRAWN, WITHDRAW, ILLEGAL),
                rejects(WITHDRAWN, EXPIRE, ILLEGAL),
                rejects(EXPIRED, OPEN, ILLEGAL),
                rejects(EXPIRED, APPROVE, ILLEGAL),
                rejects(EXPIRED, REJECT, ILLEGAL),
                rejects(EXPIRED, WITHDRAW, ILLEGAL),
                rejects(EXPIRED, EXPIRE, ILLEGAL));
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static Person person(long id) {
        Person person = new Person();
        ReflectionTestUtils.setField(person, "id", id);
        return person;
    }

    private static AuthorizationRequest requestIn(AuthorizationRequestStatus status) {
        AuthorizationRequest request = AuthorizationRequest.open(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS, "{}", AuthorizationRole.DIRECTOR,
                "why", person(1L), null, NOW);
        switch (status) {
            case PENDING -> {
            }
            case APPROVED -> request.approve(person(2L), "ok", NOW);
            case REJECTED -> request.reject(person(2L), "no", NOW);
            case WITHDRAWN -> request.withdraw(person(1L), NOW);
            case EXPIRED -> request.expire(NOW);
        }
        return request;
    }

    private static void apply(AuthorizationRequest request, AuthorizationRequestEvent event) {
        switch (event) {
            case OPEN -> ReflectionTestUtils.invokeMethod(request, "moveTo", OPEN, StatusChangeActor.ofPerson(1L), NOW, "again");
            case APPROVE -> request.approve(person(2L), "ok", NOW);
            case REJECT -> request.reject(person(2L), "no", NOW);
            case WITHDRAW -> request.withdraw(person(1L), NOW);
            case EXPIRE -> request.expire(NOW);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        AuthorizationRequestStatus from = (AuthorizationRequestStatus) expectation.fromState();
        AuthorizationRequestEvent event = (AuthorizationRequestEvent) expectation.event();
        if (from == null) {
            assertThat(AuthorizationRequestStateMachine.findTargetStateFor(null, event)).isEqualTo(java.util.Optional.ofNullable((AuthorizationRequestStatus) expectation.toState()));
            return;
        }
        AuthorizationRequest request = requestIn(from);
        int changes = request.getStatusChanges().size();
        if (expectation.isAllowed()) {
            apply(request, event);
            assertThat(request.getStatus()).isEqualTo(expectation.toState());
            assertThat(request.getStatusChanges()).hasSize(changes + 1);
            AuthorizationRequestStatusChange last = request.getStatusChanges().get(changes);
            assertThat(last.getFromStatus()).isEqualTo(from);
            assertThat(last.getToStatus()).isEqualTo(expectation.toState());
            assertThat(last.getEvent()).isEqualTo(event);
        } else {
            assertThatThrownBy(() -> apply(request, event)).isInstanceOf(expectation.rejection());
            assertThat(request.getStatus()).isEqualTo(from);
            assertThat(request.getStatusChanges()).hasSize(changes);
        }
    }

    @Test
    void theTableListsOnlyTheFiveAllowedTransitions() {
        assertThat(AuthorizationRequestStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(5);
    }
}
