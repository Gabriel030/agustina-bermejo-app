package com.red_escuela.backend.shared.domain.policy;

import java.util.List;
import java.util.Optional;

public record PolicyEvaluation(List<PolicyVerdict> verdicts) {

    public PolicyEvaluation {
        verdicts = List.copyOf(verdicts);
    }

    public boolean allowed() {
        return verdicts.stream().allMatch(PolicyVerdict::allowed);
    }

    public Optional<PolicyVerdict> firstDenial() {
        return verdicts.stream().filter(v -> !v.allowed()).findFirst();
    }

    public List<PolicyVerdict> denials() {
        return verdicts.stream().filter(v -> !v.allowed()).toList();
    }

    public String describeDenials() {
        return denials().stream().map(PolicyVerdict::code).toList().toString();
    }
}
