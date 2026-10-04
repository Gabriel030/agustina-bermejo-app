package com.red_escuela.backend.shared.domain.policy;

public record PolicyVerdict(String policy, boolean allowed, String code, String reason) {

    public static PolicyVerdict allow(String policy) {
        return new PolicyVerdict(policy, true, null, null);
    }

    public static PolicyVerdict deny(String policy, String code, String reason) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("A denial needs a code");
        }
        return new PolicyVerdict(policy, false, code, reason);
    }
}
