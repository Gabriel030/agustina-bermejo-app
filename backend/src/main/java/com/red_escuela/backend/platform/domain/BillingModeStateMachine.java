package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.exception.InvalidBillingModeTransitionException;
import com.red_escuela.backend.shared.module.BillingMode;

import java.util.Map;

public final class BillingModeStateMachine {

    private enum Requirement {
        NONE,
        CUTOVER,
        PREFLIGHT
    }

    private static final Map<BillingMode, Map<BillingMode, Requirement>> TRANSITIONS = Map.of(
            BillingMode.OFF, Map.of(BillingMode.MIRROR, Requirement.NONE, BillingMode.FULL, Requirement.NONE),
            BillingMode.MIRROR, Map.of(BillingMode.OFF, Requirement.NONE, BillingMode.FULL, Requirement.CUTOVER),
            BillingMode.FULL, Map.of(BillingMode.MIRROR, Requirement.PREFLIGHT, BillingMode.OFF, Requirement.PREFLIGHT));

    private BillingModeStateMachine() {
    }

    public static void requireTransitionAllowed(BillingMode from, BillingMode to, BillingModeChangeClearance clearance) {
        Requirement requirement = TRANSITIONS.get(from).get(to);
        if (requirement == null) {
            throw InvalidBillingModeTransitionException.notAllowed(from, to);
        }
        if (requirement == Requirement.CUTOVER && !clearance.cutoverCompleted()) {
            throw InvalidBillingModeTransitionException.cutoverRequired(from, to);
        }
        if (requirement == Requirement.PREFLIGHT && !clearance.preflightPassed()) {
            throw InvalidBillingModeTransitionException.preflightRequired(from, to);
        }
    }
}
