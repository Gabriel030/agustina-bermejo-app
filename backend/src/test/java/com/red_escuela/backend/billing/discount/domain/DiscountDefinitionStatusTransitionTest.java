package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.exception.IllegalDiscountDefinitionTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.discount.domain.DiscountDefinitionEvent.*;
import static com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-217")
@Tag("D-036")
@ExhaustiveTransitionExpectations(machine = DiscountDefinitionStateMachine.class)
class DiscountDefinitionStatusTransitionTest {
    private static final Instant NOW = Instant.parse("2031-03-10T10:00:00Z");
    private static final Class<IllegalDiscountDefinitionTransitionException> ILLEGAL = IllegalDiscountDefinitionTransitionException.class;

    private static final Map<DiscountDefinitionStatus, Map<DiscountDefinitionEvent, DiscountDefinitionStatus>> ALLOWED = Map.of(
            ACTIVE, Map.of(DEFINITION_CLOSED, CLOSED));
    private static final Map<DiscountDefinitionEvent, DiscountDefinitionStatus> INITIAL = Map.of(DEFINITION_CREATED, ACTIVE);

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (DiscountDefinitionEvent event : DiscountDefinitionEvent.values()) {
            expectations.add(INITIAL.containsKey(event) ? TransitionExpectation.allows(null, event, INITIAL.get(event)) : TransitionExpectation.rejects(null, event, ILLEGAL));
        }
        for (DiscountDefinitionStatus from : DiscountDefinitionStatus.values()) {
            for (DiscountDefinitionEvent event : DiscountDefinitionEvent.values()) {
                DiscountDefinitionStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target) : TransitionExpectation.rejects(from, event, ILLEGAL));
            }
        }
        return expectations;
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static DiscountDefinition definitionIn(DiscountDefinitionStatus status) {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().build(), 1L, NOW);
        if (status == CLOSED) {
            definition.closeDiscountDefinition("cierre", StatusChangeActor.ofPerson(1L), NOW);
        }
        return definition;
    }

    private static void apply(DiscountDefinition definition, DiscountDefinitionEvent event) {
        ReflectionTestUtils.invokeMethod(definition, "moveTo", event, StatusChangeActor.ofPerson(1L), NOW, "x");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        DiscountDefinitionStatus from = (DiscountDefinitionStatus) expectation.fromState();
        DiscountDefinitionEvent event = (DiscountDefinitionEvent) expectation.event();
        if (from == null) {
            assertThat(DiscountDefinitionStateMachine.findTargetStateFor(null, event)).isEqualTo(Optional.ofNullable((DiscountDefinitionStatus) expectation.toState()));
            return;
        }
        DiscountDefinition definition = definitionIn(from);
        int changes = definition.getStatusChanges().size();
        if (expectation.isAllowed()) {
            apply(definition, event);
            assertThat(definition.getStatus()).isEqualTo(expectation.toState());
            assertThat(definition.getStatusChanges()).hasSize(changes + 1);
            DiscountDefinitionStatusChange last = definition.getStatusChanges().get(changes);
            assertThat(last.getFromStatus()).isEqualTo(from);
            assertThat(last.getToStatus()).isEqualTo(expectation.toState());
            assertThat(last.getEvent()).isEqualTo(event);
        } else {
            assertThatThrownBy(() -> apply(definition, event)).isInstanceOf(expectation.rejection());
            assertThat(definition.getStatus()).isEqualTo(from);
            assertThat(definition.getStatusChanges()).hasSize(changes);
        }
    }

    @Test
    void theTableListsOnlyTheTwoAllowedTransitions() {
        assertThat(DiscountDefinitionStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(2);
    }
}
