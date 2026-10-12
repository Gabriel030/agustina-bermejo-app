package com.red_escuela.backend.shared.domain.statemachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class StateTransitionTable<S extends Enum<S>, E extends Enum<E>> {

    private final Class<S> stateType;
    private final Class<E> eventType;
    private final Map<E, S> initialTransitions;
    private final Map<S, Map<E, S>> transitions;

    private StateTransitionTable(Class<S> stateType, Class<E> eventType, Map<E, S> initialTransitions, Map<S, Map<E, S>> transitions) {
        this.stateType = stateType;
        this.eventType = eventType;
        this.initialTransitions = initialTransitions;
        this.transitions = transitions;
    }

    public static <S extends Enum<S>, E extends Enum<E>> Builder<S, E> forStatesAndEvents(Class<S> states, Class<E> events) {
        return new Builder<>(Objects.requireNonNull(states, "states"), Objects.requireNonNull(events, "events"));
    }

    public Optional<S> findTargetStateFor(S fromState, E event) {
        Objects.requireNonNull(event, "event");
        if (fromState == null) {
            return Optional.ofNullable(initialTransitions.get(event));
        }
        return Optional.ofNullable(transitions.getOrDefault(fromState, Map.of()).get(event));
    }

    public Set<StateTransitionRow<S, E>> listAllowedTransitions() {
        Set<StateTransitionRow<S, E>> rows = new LinkedHashSet<>();
        initialTransitions.forEach((event, target) -> rows.add(new StateTransitionRow<>(null, event, target)));
        transitions.forEach((from, byEvent) -> byEvent.forEach((event, target) -> rows.add(new StateTransitionRow<>(from, event, target))));
        return Collections.unmodifiableSet(rows);
    }

    public Class<S> stateType() {
        return stateType;
    }

    public Class<E> eventType() {
        return eventType;
    }

    public static final class Builder<S extends Enum<S>, E extends Enum<E>> {

        private final Class<S> stateType;
        private final Class<E> eventType;
        private final Map<E, S> initialTransitions;
        private final Map<S, Map<E, S>> transitions;
        private final List<String> duplicates = new ArrayList<>();

        private Builder(Class<S> stateType, Class<E> eventType) {
            this.stateType = stateType;
            this.eventType = eventType;
            this.initialTransitions = new EnumMap<>(eventType);
            this.transitions = new EnumMap<>(stateType);
        }

        public Builder<S, E> allowInitialTransition(E event, S toState) {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(toState, "toState");
            if (initialTransitions.put(event, toState) != null) {
                duplicates.add("(initial, " + event + ")");
            }
            return this;
        }

        public Builder<S, E> allowTransition(S fromState, E event, S toState) {
            Objects.requireNonNull(fromState, "fromState");
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(toState, "toState");
            if (transitions.computeIfAbsent(fromState, state -> new EnumMap<>(eventType)).put(event, toState) != null) {
                duplicates.add("(" + fromState + ", " + event + ")");
            }
            return this;
        }

        public StateTransitionTable<S, E> buildTransitionTable() {
            if (!duplicates.isEmpty()) {
                throw new IllegalStateException("Duplicated transitions: " + String.join(", ", duplicates));
            }
            return new StateTransitionTable<>(stateType, eventType, new EnumMap<>(initialTransitions), copyTransitions());
        }

        private Map<S, Map<E, S>> copyTransitions() {
            Map<S, Map<E, S>> copy = new EnumMap<>(stateType);
            transitions.forEach((from, byEvent) -> copy.put(from, new EnumMap<>(byEvent)));
            return copy;
        }
    }
}
