package com.red_escuela.backend.billing.planning;

public interface ChargePlanner<F> {
    ChargePlan plan(F facts);
}
