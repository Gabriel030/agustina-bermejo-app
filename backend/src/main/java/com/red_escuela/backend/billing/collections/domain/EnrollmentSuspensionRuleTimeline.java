package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;

@FunctionalInterface
public interface EnrollmentSuspensionRuleTimeline {
    EnrollmentSuspensionRule ruleAtCutoff(LocalDate cutoff);
}
