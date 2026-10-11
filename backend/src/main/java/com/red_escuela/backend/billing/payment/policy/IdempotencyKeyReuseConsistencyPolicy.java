package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "IDEMPOTENCY_KEY_CONFLICT", decisions = {"D-136"})
public final class IdempotencyKeyReuseConsistencyPolicy implements Policy<IdempotencyKeyReuseContext> {

    @Override
    public String ruleName() {
        return "IdempotencyKeyReuseConsistencyPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(IdempotencyKeyReuseContext context) {
        boolean sameRequest = context.storedOperationType().equals(context.operationType()) && context.storedRequestHash().equals(context.requestHash());
        return sameRequest
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "IDEMPOTENCY_KEY_CONFLICT", "La clave de idempotencia ya se usó con otro pedido");
    }
}
