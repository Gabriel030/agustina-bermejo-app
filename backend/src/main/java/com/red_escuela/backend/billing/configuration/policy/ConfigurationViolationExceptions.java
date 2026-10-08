package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.exception.CancellationReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.ChangeReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.exception.RetroactiveEffectiveDateException;
import com.red_escuela.backend.billing.configuration.exception.VersionAlreadyEffectiveException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class ConfigurationViolationExceptions {

    private ConfigurationViolationExceptions() {
    }

    public static RuntimeException forChangeViolations(List<PolicyViolation> violations) {
        return "CHANGE_REASON_REQUIRED".equals(violations.get(0).code()) ? new ChangeReasonRequiredException(violations) : new RetroactiveEffectiveDateException(violations);
    }

    public static RuntimeException forCancellationViolations(List<PolicyViolation> violations) {
        return "CANCELLATION_REASON_REQUIRED".equals(violations.get(0).code()) ? new CancellationReasonRequiredException(violations) : new VersionAlreadyEffectiveException(violations);
    }

    public static RuntimeException proposalAlreadyOpen() {
        return new ProposalAlreadyOpenException(List.of(new PolicyViolation("PROPOSAL_ALREADY_OPEN", "Ya hay una propuesta pendiente sobre este tema.")));
    }
}
