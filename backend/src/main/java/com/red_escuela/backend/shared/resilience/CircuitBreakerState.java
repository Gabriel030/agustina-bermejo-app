package com.red_escuela.backend.shared.resilience;

public enum CircuitBreakerState {
    CLOSED(0),
    OPEN(1),
    HALF_OPEN(2);

    private final int gaugeValue;

    CircuitBreakerState(int gaugeValue) {
        this.gaugeValue = gaugeValue;
    }

    public int gaugeValue() {
        return gaugeValue;
    }
}
