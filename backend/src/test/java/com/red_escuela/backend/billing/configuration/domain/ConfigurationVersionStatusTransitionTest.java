package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionEvent.*;
import static com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-095")
@Tag("D-115")
@Tag("D-180")
@ExhaustiveTransitionExpectations(machine = ConfigurationVersionStateMachine.class)
class ConfigurationVersionStatusTransitionTest {
    private static final Instant NOW = Instant.parse("2027-11-15T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 11, 15);
    private static final Class<IllegalConfigurationVersionStatusTransitionException> ILLEGAL = IllegalConfigurationVersionStatusTransitionException.class;
    private static final ConfigurationTopic TOPIC = ConfigurationTopic.ofGlobal(ConfigurationScope.ALLOCATION);

    private static final Map<ConfigurationVersionStatus, Map<ConfigurationVersionEvent, ConfigurationVersionStatus>> ALLOWED = Map.of(
            PROPOSED, Map.of(APPROVE_FOR_FUTURE, SCHEDULED, APPROVE_FOR_TODAY, EFFECTIVE, REJECT, REJECTED, WITHDRAW, WITHDRAWN, EXPIRE, EXPIRED),
            SCHEDULED, Map.of(REACH_START, EFFECTIVE, CANCEL, CANCELLED),
            EFFECTIVE, Map.of(SUPERSEDE, SUPERSEDED));
    private static final Map<ConfigurationVersionEvent, ConfigurationVersionStatus> INITIAL = Map.of(
            PROPOSE, PROPOSED, PUBLISH_FOR_FUTURE, SCHEDULED, PUBLISH_FOR_TODAY, EFFECTIVE);

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (ConfigurationVersionEvent event : ConfigurationVersionEvent.values()) {
            expectations.add(INITIAL.containsKey(event) ? TransitionExpectation.allows(null, event, INITIAL.get(event)) : TransitionExpectation.rejects(null, event, ILLEGAL));
        }
        for (ConfigurationVersionStatus from : ConfigurationVersionStatus.values()) {
            for (ConfigurationVersionEvent event : ConfigurationVersionEvent.values()) {
                ConfigurationVersionStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target) : TransitionExpectation.rejects(from, event, ILLEGAL));
            }
        }
        return expectations;
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static ConfigurationVersion versionIn(ConfigurationVersionStatus status) {
        ConfigurationVersion version = ConfigurationVersion.propose(TOPIC, new AllocationSettings(true), TODAY.plusDays(10), "why", 1L, 5L, null, NOW);
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

    private static void apply(ConfigurationVersion version, ConfigurationVersionEvent event) {
        ReflectionTestUtils.invokeMethod(version, "moveTo", event, StatusChangeActor.ofPerson(1L), NOW, "x");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        ConfigurationVersionStatus from = (ConfigurationVersionStatus) expectation.fromState();
        ConfigurationVersionEvent event = (ConfigurationVersionEvent) expectation.event();
        if (from == null) {
            assertThat(ConfigurationVersionStateMachine.findTargetStateFor(null, event)).isEqualTo(java.util.Optional.ofNullable((ConfigurationVersionStatus) expectation.toState()));
            return;
        }
        ConfigurationVersion version = versionIn(from);
        int changes = version.getStatusChanges().size();
        if (expectation.isAllowed()) {
            apply(version, event);
            assertThat(version.getStatus()).isEqualTo(expectation.toState());
            assertThat(version.getStatusChanges()).hasSize(changes + 1);
            ConfigurationVersionStatusChange last = version.getStatusChanges().get(changes);
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
        assertThat(ConfigurationVersionStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(11);
    }

    @Test
    void approvalPicksTheEventByStartDateAndRecordsTheDecision() {
        ConfigurationVersion future = ConfigurationVersion.propose(TOPIC, new AllocationSettings(true), TODAY.plusDays(1), "why", 1L, 5L, null, NOW);
        future.approve(2L, "ok", TODAY, NOW);
        ConfigurationVersion today = ConfigurationVersion.propose(TOPIC, new AllocationSettings(true), TODAY, "why", 1L, 6L, null, NOW);
        today.approve(2L, "ok", TODAY, NOW);
        assertThat(future.getStatus()).isEqualTo(SCHEDULED);
        assertThat(today.getStatus()).isEqualTo(EFFECTIVE);
        assertThat(future.getDecidedById()).isEqualTo(2L);
        assertThat(future.getDecidedAt()).isEqualTo(NOW);
        assertThat(future.getDecisionReason()).isEqualTo("ok");
        assertThat(future.getAuthorizationRequestId()).isEqualTo(5L);
    }

    @Test
    void directPublicationIsAuditedAndPicksTheEventByStartDate() {
        ConfigurationVersion future = ConfigurationVersion.publish(TOPIC, new AllocationSettings(true), TODAY.plusDays(1), "why", 2L, TODAY, null, NOW);
        ConfigurationVersion today = ConfigurationVersion.publish(TOPIC, new AllocationSettings(false), TODAY, "why", 2L, TODAY, 9L, NOW);
        assertThat(future.getStatus()).isEqualTo(SCHEDULED);
        assertThat(today.getStatus()).isEqualTo(EFFECTIVE);
        assertThat(future.isDirectPublication()).isTrue();
        assertThat(future.getAuthorizationRequestId()).isNull();
        assertThat(today.getPreviousVersionId()).isEqualTo(9L);
        assertThat(today.values()).isEqualTo(new AllocationSettings(false));
        assertThat(future.getDecidedById()).isEqualTo(2L);
    }

    @Test
    void cancellationKeepsWhoWhenAndWhy() {
        ConfigurationVersion version = versionIn(CANCELLED);
        assertThat(version.getCancelledById()).isEqualTo(2L);
        assertThat(version.getCancelledAt()).isEqualTo(NOW);
        assertThat(version.getCancellationReason()).isEqualTo("motivo");
    }

    @Test
    void valuesOfAnotherScopeAreRejected() {
        assertThatThrownBy(() -> ConfigurationVersion.propose(TOPIC, new CourseTerms(10), TODAY, "why", 1L, 5L, null, NOW))
                .isInstanceOf(InvalidConfigurationValuesException.class);
    }
}
