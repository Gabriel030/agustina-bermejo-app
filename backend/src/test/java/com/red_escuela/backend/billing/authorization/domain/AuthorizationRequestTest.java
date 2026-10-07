package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-176")
class AuthorizationRequestTest {
    private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-08T10:00:00Z");

    private static Person person(long id) {
        Person person = new Person();
        ReflectionTestUtils.setField(person, "id", id);
        return person;
    }

    private AuthorizationRequest openRequest() {
        return AuthorizationRequest.open(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS, "{}", AuthorizationRole.DIRECTOR, "why",
                person(1L), LocalDate.of(2026, 12, 1), T0);
    }

    @Test
    void aNewRequestIsPendingWithItsOpeningInTheHistory() {
        AuthorizationRequest request = openRequest();

        assertThat(request.getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(request.isPending()).isTrue();
        assertThat(request.getRequestedAt()).isEqualTo(T0);
        assertThat(request.getExpiresOn()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(request.getStatusChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFromStatus()).isNull();
            assertThat(change.getToStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
            assertThat(change.getEvent()).isEqualTo(AuthorizationRequestEvent.OPEN);
            assertThat(change.getActorPersonId()).isEqualTo(1L);
            assertThat(change.getActorProcess()).isNull();
            assertThat(change.getChangedAt()).isEqualTo(T0);
            assertThat(change.getReason()).isEqualTo("why");
        });
    }

    @Test
    void approvingRecordsTheDeciderAndTheHistory() {
        AuthorizationRequest request = openRequest();
        Person decider = person(2L);

        request.approve(decider, "ok", T1);

        assertThat(request.getStatus()).isEqualTo(AuthorizationRequestStatus.APPROVED);
        assertThat(request.isPending()).isFalse();
        assertThat(request.getDecidedBy()).isSameAs(decider);
        assertThat(request.getDecidedAt()).isEqualTo(T1);
        assertThat(request.getDecisionReason()).isEqualTo("ok");
        assertThat(request.getStatusChanges()).hasSize(2);
        assertThat(request.getStatusChanges().get(1).getFromStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(request.getStatusChanges().get(1).getToStatus()).isEqualTo(AuthorizationRequestStatus.APPROVED);
        assertThat(request.getStatusChanges().get(1).getActorPersonId()).isEqualTo(2L);
    }

    @Test
    void rejectingWithdrawingAndExpiringLeaveTheirOwnStatus() {
        AuthorizationRequest rejected = openRequest();
        rejected.reject(person(2L), "no", T1);
        AuthorizationRequest withdrawn = openRequest();
        withdrawn.withdraw(person(1L), T1);
        AuthorizationRequest expired = openRequest();
        expired.expire(T1);

        assertThat(rejected.getStatus()).isEqualTo(AuthorizationRequestStatus.REJECTED);
        assertThat(rejected.getDecisionReason()).isEqualTo("no");
        assertThat(withdrawn.getStatus()).isEqualTo(AuthorizationRequestStatus.WITHDRAWN);
        assertThat(withdrawn.getDecidedBy().getId()).isEqualTo(1L);
        assertThat(withdrawn.getDecisionReason()).isNull();
        assertThat(expired.getStatus()).isEqualTo(AuthorizationRequestStatus.EXPIRED);
        assertThat(expired.getDecidedAt()).isEqualTo(T1);
        assertThat(expired.getDecidedBy()).isNull();
        assertThat(expired.getStatusChanges().get(1).getActorPersonId()).isNull();
        assertThat(expired.getStatusChanges().get(1).getActorProcess()).isEqualTo("AuthorizationRequestExpiration");
    }

    @Test
    void aDecidedRequestRejectsEveryFurtherDecision() {
        AuthorizationRequest request = openRequest();
        request.approve(person(2L), "ok", T1);

        assertThatThrownBy(() -> request.approve(person(2L), "again", T1)).isInstanceOf(IllegalAuthorizationRequestTransitionException.class);
        assertThatThrownBy(() -> request.reject(person(2L), "no", T1)).isInstanceOf(IllegalAuthorizationRequestTransitionException.class);
        assertThatThrownBy(() -> request.withdraw(person(1L), T1)).isInstanceOf(IllegalAuthorizationRequestTransitionException.class);
        assertThatThrownBy(() -> request.expire(T1)).isInstanceOf(IllegalAuthorizationRequestTransitionException.class)
                .hasMessageContaining("APPROVED").hasMessageContaining("EXPIRE");
        assertThat(request.getStatusChanges()).hasSize(2);
        assertThat(request.getDecisionReason()).isEqualTo("ok");
    }

    @Test
    void theResumeOutcomeCanBeRecordedOnceAndOnlyOnAnApprovedRequest() {
        AuthorizationRequest pending = openRequest();
        assertThatThrownBy(() -> pending.recordResumeOutcome(AuthorizationResumeOutcome.PROCESSED)).isInstanceOf(IllegalStateException.class);

        AuthorizationRequest approved = openRequest();
        approved.approve(person(2L), "ok", T1);
        approved.recordResumeOutcome(AuthorizationResumeOutcome.REJECTED_ON_RESUME);

        assertThat(approved.getResumeOutcome()).isEqualTo(AuthorizationResumeOutcome.REJECTED_ON_RESUME);
        assertThatThrownBy(() -> approved.recordResumeOutcome(AuthorizationResumeOutcome.PROCESSED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theStatusChangesAreExposedAsAnUnmodifiableCopy() {
        AuthorizationRequest request = openRequest();

        assertThatThrownBy(() -> request.getStatusChanges().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
