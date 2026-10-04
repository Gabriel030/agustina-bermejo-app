package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyEvaluation;
import com.red_escuela.backend.shared.exception.ConflictException;

public class ChargeRejectedException extends ConflictException {
    private final transient PolicyEvaluation evaluation;

    public ChargeRejectedException(PolicyEvaluation evaluation) {
        super("Charge rejected: " + evaluation.describeDenials());
        this.evaluation = evaluation;
    }

    public PolicyEvaluation getEvaluation() {
        return evaluation;
    }
}
