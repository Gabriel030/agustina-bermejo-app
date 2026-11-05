package com.red_escuela.backend.shared.domain.statemachine;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClass;
import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-051")
@Tag("integration")
class StateTransitionCoverageTest {

    enum Light { RED, GREEN }

    enum Signal { GO, STOP }

    private static StateTransitionTable<Light, Signal> table() {
        return StateTransitionTable.forStatesAndEvents(Light.class, Signal.class)
                .allowInitialTransition(Signal.STOP, Light.RED)
                .allowTransition(Light.RED, Signal.GO, Light.GREEN)
                .buildTransitionTable();
    }

    private static List<TransitionExpectation> fullExpectations() {
        return List.of(
                TransitionExpectation.allows(null, Signal.STOP, Light.RED),
                TransitionExpectation.rejects(null, Signal.GO, IllegalStateException.class),
                TransitionExpectation.rejects(Light.RED, Signal.STOP, IllegalStateException.class),
                TransitionExpectation.allows(Light.RED, Signal.GO, Light.GREEN),
                TransitionExpectation.rejects(Light.GREEN, Signal.GO, IllegalStateException.class),
                TransitionExpectation.rejects(Light.GREEN, Signal.STOP, IllegalStateException.class));
    }

    @Test
    void everyMachineInMainHasItsExhaustiveTestCoveringTheWholeProduct() throws Exception {
        Map<String, StateTransitionTable<?, ?>> tables = StateMachineDiscovery.findTablesDeclaredInMain();
        Map<String, List<TransitionExpectation>> expectations = new HashMap<>();
        for (JavaClass testClass : ImportedApplicationClasses.allTests()) {
            ExhaustiveTransitionExpectations annotation = testClass.reflect().getAnnotation(ExhaustiveTransitionExpectations.class);
            if (annotation != null) {
                Method method = testClass.reflect().getDeclaredMethod("expectedTransitions");
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<TransitionExpectation> list = (List<TransitionExpectation>) method.invoke(null);
                expectations.put(annotation.machine().getName(), list);
            }
        }

        assertThat(tables).isNotEmpty();
        assertThat(StateTransitionCoverageRules.findViolations(tables, expectations)).isEmpty();
    }

    @Test
    void aMachineWithoutItsExhaustiveTestFailsTheGate() {
        assertThat(StateTransitionCoverageRules.findViolations(Map.of("Machine", table()), Map.of()))
                .containsExactly("Machine has no exhaustive transition test");
    }

    @Test
    void aFullyCoveredMachinePassesTheGate() {
        assertThat(StateTransitionCoverageRules.findViolations(Map.of("Machine", table()), Map.of("Machine", fullExpectations()))).isEmpty();
    }

    @Test
    void anEventWithoutExpectationFailsTheGate() {
        List<TransitionExpectation> missingOne = fullExpectations().subList(0, 5);

        assertThat(StateTransitionCoverageRules.findViolations(Map.of("Machine", table()), Map.of("Machine", missingOne)))
                .containsExactly("Machine has no expectation for (GREEN, STOP)");
    }

    @Test
    void aRepeatedExpectationFailsTheGate() {
        List<TransitionExpectation> repeated = new java.util.ArrayList<>(fullExpectations());
        repeated.add(fullExpectations().get(0));

        assertThat(StateTransitionCoverageRules.findViolations(Map.of("Machine", table()), Map.of("Machine", repeated)))
                .containsExactly("Machine expects (none, STOP) twice");
    }

    @Test
    void expectationsThatDisagreeWithTheDeclaredTransitionsFailTheGate() {
        List<TransitionExpectation> wrong = new java.util.ArrayList<>(fullExpectations());
        wrong.set(3, TransitionExpectation.rejects(Light.RED, Signal.GO, IllegalStateException.class));

        assertThat(StateTransitionCoverageRules.findViolations(Map.of("Machine", table()), Map.of("Machine", wrong)))
                .hasSize(1)
                .allSatisfy(violation -> assertThat(violation).contains("differ").contains("(RED, GO) -> GREEN"));
    }
}
