package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.exception.ConflictException;

public class ChargeRejectedException extends ConflictException {
    private final transient PolicyEvaluationResult evaluation;

    public ChargeRejectedException(PolicyEvaluationResult evaluation) {
        super("Charge rejected: " + evaluation.describeDenials());
        this.evaluation = evaluation;
    }

    public PolicyEvaluationResult getEvaluation() {
        return evaluation;
    }
}
