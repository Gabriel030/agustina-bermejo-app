package com.red_escuela.backend.shared.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public interface ReportsPolicyViolations {
    List<PolicyViolation> violations();
}
