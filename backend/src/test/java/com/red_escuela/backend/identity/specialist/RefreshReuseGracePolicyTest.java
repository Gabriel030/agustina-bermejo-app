package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.specialist.RefreshReuseGracePolicy.Verdict;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshReuseGracePolicyTest {

    private static final LocalDateTime ROTATED_AT = LocalDateTime.of(2026, 11, 7, 10, 0, 0);
    private static final ClientContext CLIENT = ClientContext.of("browser-a", "10.0.0.1");

    private final RefreshReuseGracePolicy policy = new RefreshReuseGracePolicy(30);

    private Person person(StatusType status) {
        Person person = new Person();
        person.setId(7L);
        person.setStatus(status);
        return person;
    }

    private StubRefreshTokenState rotatedByCurrentClient() {
        return new StubRefreshTokenState(person(StatusType.ACTIVE), ROTATED_AT.plusDays(7))
                .rotated(ROTATED_AT, CLIENT.userAgentHash());
    }

    @Test
    void reuseExactlyAtTheEndOfTheWindowIsAllowed() {
        assertThat(policy.evaluate(rotatedByCurrentClient(), CLIENT, ROTATED_AT.plusSeconds(30)))
                .isEqualTo(Verdict.GRACE_ALLOWED);
    }

    @Test
    void reuseOneSecondAfterTheWindowIsOutsideWindow() {
        assertThat(policy.evaluate(rotatedByCurrentClient(), CLIENT, ROTATED_AT.plusSeconds(31)))
                .isEqualTo(Verdict.OUTSIDE_WINDOW);
    }

    @Test
    void reuseWithDifferentUserAgentIsRejected() {
        ClientContext other = ClientContext.of("browser-b", "10.0.0.1");

        assertThat(policy.evaluate(rotatedByCurrentClient(), other, ROTATED_AT.plusSeconds(1)))
                .isEqualTo(Verdict.USER_AGENT_MISMATCH);
    }

    @Test
    void reuseFromDifferentIpWithSameUserAgentIsAllowed() {
        ClientContext otherNetwork = ClientContext.of("browser-a", "203.0.113.9");

        assertThat(policy.evaluate(rotatedByCurrentClient(), otherNetwork, ROTATED_AT.plusSeconds(1)))
                .isEqualTo(Verdict.GRACE_ALLOWED);
    }

    @Test
    void tokenRevokedWithoutRotationMarkNeverGetsGrace() {
        StubRefreshTokenState loggedOut = new StubRefreshTokenState(person(StatusType.ACTIVE), ROTATED_AT.plusDays(7)).revoked();

        assertThat(policy.evaluate(loggedOut, CLIENT, ROTATED_AT.plusSeconds(1))).isEqualTo(Verdict.NOT_ROTATED);
    }

    @Test
    void tokenWhoseGraceWasAlreadyUsedIsRejected() {
        assertThat(policy.evaluate(rotatedByCurrentClient().graceAlreadyUsed(), CLIENT, ROTATED_AT.plusSeconds(1)))
                .isEqualTo(Verdict.GRACE_ALREADY_USED);
    }

    @Test
    void inactivePersonNeverGetsGrace() {
        StubRefreshTokenState token = new StubRefreshTokenState(person(StatusType.INACTIVE), ROTATED_AT.plusDays(7))
                .rotated(ROTATED_AT, CLIENT.userAgentHash());

        assertThat(policy.evaluate(token, CLIENT, ROTATED_AT.plusSeconds(1))).isEqualTo(Verdict.PERSON_INACTIVE);
    }

    @Test
    void graceDisabledWhenSecondsIsZeroBehavesStrict() {
        RefreshReuseGracePolicy strict = new RefreshReuseGracePolicy(0);

        assertThat(strict.evaluate(rotatedByCurrentClient(), CLIENT, ROTATED_AT)).isEqualTo(Verdict.GRACE_DISABLED);
    }

    @Test
    void propertyOutsideRangeFailsStartup() {
        assertThatThrownBy(() -> new RefreshReuseGracePolicy(-1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RefreshReuseGracePolicy(121)).isInstanceOf(IllegalStateException.class);
        assertThat(new RefreshReuseGracePolicy(120)).isNotNull();
    }

    @Test
    void missingOrBlankUserAgentNeverMatches() {
        ClientContext blank = ClientContext.of("  ", "10.0.0.1");
        StubRefreshTokenState rotatedWithoutAgent = new StubRefreshTokenState(person(StatusType.ACTIVE), ROTATED_AT.plusDays(7))
                .rotated(ROTATED_AT, blank.userAgentHash());

        assertThat(policy.evaluate(rotatedWithoutAgent, blank, ROTATED_AT.plusSeconds(1))).isEqualTo(Verdict.USER_AGENT_MISMATCH);
        assertThat(policy.evaluate(rotatedByCurrentClient(), ClientContext.of(null, "10.0.0.1"), ROTATED_AT.plusSeconds(1)))
                .isEqualTo(Verdict.USER_AGENT_MISMATCH);
    }
}
