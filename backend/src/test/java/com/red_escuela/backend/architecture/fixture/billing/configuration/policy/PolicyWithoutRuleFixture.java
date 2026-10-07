package com.red_escuela.backend.architecture.fixture.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

public class PolicyWithoutRuleFixture implements Policy<String> {

    @Override
    public String ruleName() {
        return "PolicyWithoutRuleFixture";
    }

    @Override
    public PolicyVerdict evaluateRule(String context) {
        return PolicyVerdict.allow(ruleName());
    }
}
