package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;
import java.util.function.Function;

public sealed interface EnrollmentFeeRequirementOutcome {

    void resolveEnrollmentFeeRequirement(EnrollmentFeeOnDemandIssuer issuer, Function<List<PolicyViolation>, ? extends RuntimeException> blockedExceptionFactory);

    record NotRequired(String reasonCode) implements EnrollmentFeeRequirementOutcome {
        public void resolveEnrollmentFeeRequirement(EnrollmentFeeOnDemandIssuer issuer, Function<List<PolicyViolation>, ? extends RuntimeException> blockedExceptionFactory) {
        }
    }

    record Settled() implements EnrollmentFeeRequirementOutcome {
        public void resolveEnrollmentFeeRequirement(EnrollmentFeeOnDemandIssuer issuer, Function<List<PolicyViolation>, ? extends RuntimeException> blockedExceptionFactory) {
        }
    }

    record Block(PolicyEvaluationResult requirementResult) implements EnrollmentFeeRequirementOutcome {
        public void resolveEnrollmentFeeRequirement(EnrollmentFeeOnDemandIssuer issuer, Function<List<PolicyViolation>, ? extends RuntimeException> blockedExceptionFactory) {
            requirementResult.throwIfDenied(blockedExceptionFactory);
        }
    }

    record IssueOnDemandAndBlock(PlannedInvoice charge, PolicyEvaluationResult requirementResult) implements EnrollmentFeeRequirementOutcome {
        public void resolveEnrollmentFeeRequirement(EnrollmentFeeOnDemandIssuer issuer, Function<List<PolicyViolation>, ? extends RuntimeException> blockedExceptionFactory) {
            issuer.issueEnrollmentFeeOnDemand(charge);
            requirementResult.throwIfDenied(blockedExceptionFactory);
        }
    }
}
