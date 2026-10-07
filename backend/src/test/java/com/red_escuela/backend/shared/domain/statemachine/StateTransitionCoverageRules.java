package com.red_escuela.backend.shared.domain.statemachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StateTransitionCoverageRules {

    private StateTransitionCoverageRules() {
    }

    public static List<String> findViolations(Map<String, StateTransitionTable<?, ?>> tablesByMachine,
                                              Map<String, List<TransitionExpectation>> expectationsByMachine) {
        List<String> violations = new ArrayList<>();
        tablesByMachine.forEach((machine, table) -> {
            List<TransitionExpectation> expectations = expectationsByMachine.get(machine);
            if (expectations == null) {
                violations.add(machine + " has no exhaustive transition test");
                return;
            }
            checkMachine(machine, table, expectations, violations);
        });
        return violations;
    }

    private static void checkMachine(String machine, StateTransitionTable<?, ?> table, List<TransitionExpectation> expectations,
                                     List<String> violations) {
        Set<String> covered = new HashSet<>();
        for (TransitionExpectation expectation : expectations) {
            if (!covered.add(pairOf(expectation.fromState(), expectation.event()))) {
                violations.add(machine + " expects " + pairOf(expectation.fromState(), expectation.event()) + " twice");
            }
        }
        List<Enum<?>> states = new ArrayList<>();
        states.add(null);
        states.addAll(List.of(table.stateType().getEnumConstants()));
        for (Enum<?> state : states) {
            for (Object event : table.eventType().getEnumConstants()) {
                if (!covered.contains(pairOf(state, (Enum<?>) event))) {
                    violations.add(machine + " has no expectation for " + pairOf(state, (Enum<?>) event));
                }
            }
        }
        Set<String> declared = new HashSet<>();
        table.listAllowedTransitions().forEach(row -> declared.add(rowOf((Enum<?>) row.fromState(), (Enum<?>) row.event(), (Enum<?>) row.toState())));
        Set<String> expected = new HashSet<>();
        expectations.stream().filter(TransitionExpectation::isAllowed)
                .forEach(e -> expected.add(rowOf(e.fromState(), e.event(), e.toState())));
        if (!declared.equals(expected)) {
            Set<String> difference = new HashSet<>(declared);
            difference.addAll(expected);
            Set<String> common = new HashSet<>(declared);
            common.retainAll(expected);
            difference.removeAll(common);
            violations.add(machine + " expectations differ from the declared transitions: " + difference);
        }
    }

    private static String pairOf(Enum<?> state, Enum<?> event) {
        return "(" + (state == null ? "none" : state.name()) + ", " + event.name() + ")";
    }

    private static String rowOf(Enum<?> state, Enum<?> event, Enum<?> target) {
        return pairOf(state, event) + " -> " + target.name();
    }
}
