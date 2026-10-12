package com.red_escuela.backend.shared.domain.statemachine;

public record TransitionExpectation(Enum<?> fromState, Enum<?> event, Enum<?> toState, Class<? extends Throwable> rejection) {

    public static TransitionExpectation allows(Enum<?> fromState, Enum<?> event, Enum<?> toState) {
        return new TransitionExpectation(fromState, event, toState, null);
    }

    public static TransitionExpectation rejects(Enum<?> fromState, Enum<?> event, Class<? extends Throwable> rejection) {
        return new TransitionExpectation(fromState, event, null, rejection);
    }

    public boolean isAllowed() {
        return toState != null;
    }
}
