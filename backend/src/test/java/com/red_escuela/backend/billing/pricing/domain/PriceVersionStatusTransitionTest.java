package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.pricing.domain.PriceVersionEvent.*;
import static com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-214")
@Tag("D-115")
@Tag("D-180")
@ExhaustiveTransitionExpectations(machine = PriceVersionStateMachine.class)
class PriceVersionStatusTransitionTest {
    private static final Instant NOW = Instant.parse("2027-11-15T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 11, 15);
    private static final Class<IllegalPriceVersionStatusTransitionException> ILLEGAL = IllegalPriceVersionStatusTransitionException.class;

    private static final Map<PriceVersionStatus, Map<PriceVersionEvent, PriceVersionStatus>> ALLOWED = Map.of(
            PROPOSED, Map.of(APPROVE_FOR_FUTURE, SCHEDULED, APPROVE_FOR_TODAY, EFFECTIVE, REJECT, REJECTED, WITHDRAW, WITHDRAWN, EXPIRE, EXPIRED),
            SCHEDULED, Map.of(REACH_START, EFFECTIVE, CANCEL, CANCELLED),
            EFFECTIVE, Map.of(SUPERSEDE, SUPERSEDED));
    private static final Map<PriceVersionEvent, PriceVersionStatus> INITIAL = Map.of(
            PROPOSE, PROPOSED, PUBLISH_FOR_FUTURE, SCHEDULED, PUBLISH_FOR_TODAY, EFFECTIVE);

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (PriceVersionEvent event : PriceVersionEvent.values()) {
            expectations.add(INITIAL.containsKey(event) ? TransitionExpectation.allows(null, event, INITIAL.get(event)) : TransitionExpectation.rejects(null, event, ILLEGAL));
        }
        for (PriceVersionStatus from : PriceVersionStatus.values()) {
            for (PriceVersionEvent event : PriceVersionEvent.values()) {
                PriceVersionStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target) : TransitionExpectation.rejects(from, event, ILLEGAL));
            }
        }
        return expectations;
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static PriceVersion versionIn(PriceVersionStatus status) {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        PriceVersion version = PriceVersion.propose(price, NonNegativeMoney.of(BigDecimal.valueOf(100000), Currency.ARS), null, TODAY.plusDays(10), "why", 1L, 5L, false, null, NOW);
        switch (status) {
            case PROPOSED -> {
            }
            case SCHEDULED -> version.approve(2L, "ok", TODAY, NOW);
            case EFFECTIVE -> version.approve(2L, "ok", TODAY.plusDays(10), NOW);
            case SUPERSEDED -> {
                version.approve(2L, "ok", TODAY.plusDays(10), NOW);
                version.supersede(NOW);
            }
            case REJECTED -> version.reject(2L, "no", NOW);
            case WITHDRAWN -> version.withdraw(1L, NOW);
            case EXPIRED -> version.expire(NOW);
            case CANCELLED -> {
                version.approve(2L, "ok", TODAY, NOW);
                version.cancel(2L, "motivo", NOW);
            }
        }
        return version;
    }

    private static void apply(PriceVersion version, PriceVersionEvent event) {
        ReflectionTestUtils.invokeMethod(version, "moveTo", event, StatusChangeActor.ofPerson(1L), NOW, "x");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        PriceVersionStatus from = (PriceVersionStatus) expectation.fromState();
        PriceVersionEvent event = (PriceVersionEvent) expectation.event();
        if (from == null) {
            assertThat(PriceVersionStateMachine.findTargetStateFor(null, event)).isEqualTo(Optional.ofNullable((PriceVersionStatus) expectation.toState()));
            return;
        }
        PriceVersion version = versionIn(from);
        int changes = version.getStatusChanges().size();
        if (expectation.isAllowed()) {
            apply(version, event);
            assertThat(version.getStatus()).isEqualTo(expectation.toState());
            assertThat(version.getStatusChanges()).hasSize(changes + 1);
            PriceVersionStatusChange last = version.getStatusChanges().get(changes);
            assertThat(last.getFromStatus()).isEqualTo(from);
            assertThat(last.getToStatus()).isEqualTo(expectation.toState());
            assertThat(last.getEvent()).isEqualTo(event);
        } else {
            assertThatThrownBy(() -> apply(version, event)).isInstanceOf(expectation.rejection());
            assertThat(version.getStatus()).isEqualTo(from);
            assertThat(version.getStatusChanges()).hasSize(changes);
        }
    }

    @Test
    void theTableListsOnlyTheElevenAllowedTransitions() {
        assertThat(PriceVersionStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(11);
    }

    @Test
    void everyStatusChangeKeepsWhoMovedTheVersionAndWhy() {
        PriceVersion version = versionIn(CANCELLED);
        PriceVersionStatusChange last = version.getStatusChanges().get(version.getStatusChanges().size() - 1);
        assertThat(last.getActorPersonId()).isEqualTo(2L);
        assertThat(last.getReason()).isEqualTo("motivo");
        assertThat(version.getCancellationReason()).isEqualTo("motivo");
        assertThat(Money.zero(Currency.ARS).isZero()).isTrue();
    }
}
