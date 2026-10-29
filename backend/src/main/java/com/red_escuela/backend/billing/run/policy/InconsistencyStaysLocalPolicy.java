package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "GLOBAL_ACCOUNT_INCONSISTENCY", decisions = {"D-356"})
public final class InconsistencyStaysLocalPolicy implements Policy<InconsistencyStaysLocalContext> {
    public static final int MINIMUM_INCONSISTENT_ACCOUNTS_TO_BE_GLOBAL = 5;

    @Override
    public String ruleName() {
        return "InconsistencyStaysLocalPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InconsistencyStaysLocalContext context) {
        boolean global = context.inconsistentAccounts() >= MINIMUM_INCONSISTENT_ACCOUNTS_TO_BE_GLOBAL && context.inconsistentAccounts() * 2 >= context.totalAccounts();
        return global
                ? PolicyVerdict.deny(ruleName(), "GLOBAL_ACCOUNT_INCONSISTENCY", "La mitad o más de las cuentas no cierran: el problema es global, no de una cuenta")
                : PolicyVerdict.allow(ruleName());
    }
}
