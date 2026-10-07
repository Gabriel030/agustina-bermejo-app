package com.red_escuela.backend.shared.domain.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoliciesAndEvaluationDescriptionTest {

    private static Policy<Integer> positive() {
        return new Policy<>() {
            @Override
            public String ruleName() {
                return "positive";
            }

            @Override
            public PolicyVerdict evaluateRule(Integer context) {
                return context > 0 ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "NOT_POSITIVE", "no es positivo");
            }
        };
    }

    @Test
    void composedPoliciesExposeTheirName() {
        assertThat(Policies.allOf("all", positive()).ruleName()).isEqualTo("all");
        assertThat(Policies.anyOf("any", positive()).ruleName()).isEqualTo("any");
        assertThat(Policies.not("negation", "IS_POSITIVE", "es positivo", positive()).ruleName()).isEqualTo("negation");
    }

    @Test
    void describeDenialsListsTheCodesOfTheDeniedVerdictsOnly() {
        PolicyEvaluationResult evaluation = new PolicyEvaluationResult(List.of(
                PolicyVerdict.allow("a"),
                PolicyVerdict.deny("b", "CODE_B", "motivo b"),
                PolicyVerdict.deny("c", "CODE_C", "motivo c")));

        assertThat(evaluation.describeDenials()).isEqualTo("[CODE_B, CODE_C]");
        assertThat(new PolicyEvaluationResult(List.of(PolicyVerdict.allow("a"))).describeDenials()).isEqualTo("[]");
    }
}
