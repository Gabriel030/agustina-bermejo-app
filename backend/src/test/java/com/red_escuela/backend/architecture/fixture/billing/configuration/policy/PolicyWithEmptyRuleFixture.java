package com.red_escuela.backend.architecture.fixture.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {}, decisions = {"D-001"})
public class PolicyWithEmptyRuleFixture implements Policy<String> {

    @Override
    public String name() {
        return "PolicyWithEmptyRuleFixture";
    }

    @Override
    public PolicyVerdict evaluate(String context) {
        return PolicyVerdict.allow(name());
    }
}
