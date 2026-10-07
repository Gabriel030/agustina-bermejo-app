package com.red_escuela.backend.shared.domain.statemachine;

public record StateTransitionRow<S, E>(S fromState, E event, S toState) {
}
