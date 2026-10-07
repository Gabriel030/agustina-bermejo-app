package com.red_escuela.backend.billing.authorization.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-176")
class AuthorizationRequestStateMachineTest {

    private static Map<String, AuthorizationRequestStatus> expected() {
        Map<String, AuthorizationRequestStatus> allowed = new HashMap<>();
        allowed.put("null/" + OPEN, PENDING);
        allowed.put(PENDING + "/" + APPROVE, APPROVED);
        allowed.put(PENDING + "/" + REJECT, REJECTED);
        allowed.put(PENDING + "/" + WITHDRAW, WITHDRAWN);
        allowed.put(PENDING + "/" + EXPIRE, EXPIRED);
        return allowed;
    }

    @Test
    void everyStatusAndEventPairHasExactlyTheDeclaredOutcome() {
        Map<String, AuthorizationRequestStatus> allowed = expected();
        int checked = 0;
        for (AuthorizationRequestStatus from : java.util.Arrays.asList(null, PENDING, APPROVED, REJECTED, WITHDRAWN, EXPIRED)) {
            for (AuthorizationRequestEvent event : AuthorizationRequestEvent.values()) {
                Optional<AuthorizationRequestStatus> target = AuthorizationRequestStateMachine.findTargetStateFor(from, event);

                assertThat(target).as(from + " x " + event).isEqualTo(Optional.ofNullable(allowed.get(from + "/" + event)));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(6 * AuthorizationRequestEvent.values().length);
    }

    @Test
    void theTableListsOnlyTheFiveAllowedTransitions() {
        assertThat(AuthorizationRequestStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(expected().size());
    }

    @Test
    void finalStatusesAcceptNoEvent() {
        for (AuthorizationRequestStatus finalStatus : java.util.List.of(APPROVED, REJECTED, WITHDRAWN, EXPIRED)) {
            for (AuthorizationRequestEvent event : AuthorizationRequestEvent.values()) {
                assertThat(AuthorizationRequestStateMachine.findTargetStateFor(finalStatus, event)).isEmpty();
            }
        }
    }
}
