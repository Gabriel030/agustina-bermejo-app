package com.red_escuela.backend.shared.domain.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoliciesAndEvaluationDescriptionTest {

    private static Policy<Integer> positive() {
        return new Policy<>() {
            @Override
            public String name() {
                return "positive";
            }

            @Override
            public PolicyVerdict evaluate(Integer context) {
                return context > 0 ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "NOT_POSITIVE", "no es positivo");
            }
        };
    }

    @Test
    void composedPoliciesExposeTheirName() {
        assertThat(Policies.allOf("all", positive()).name()).isEqualTo("all");
        assertThat(Policies.anyOf("any", positive()).name()).isEqualTo("any");
        assertThat(Policies.not("negation", "IS_POSITIVE", "es positivo", positive()).name()).isEqualTo("negation");
    }

    @Test
    void describeDenialsListsTheCodesOfTheDeniedVerdictsOnly() {
        PolicyEvaluation evaluation = new PolicyEvaluation(List.of(
                PolicyVerdict.allow("a"),
                PolicyVerdict.deny("b", "CODE_B", "motivo b"),
                PolicyVerdict.deny("c", "CODE_C", "motivo c")));

        assertThat(evaluation.describeDenials()).isEqualTo("[CODE_B, CODE_C]");
        assertThat(new PolicyEvaluation(List.of(PolicyVerdict.allow("a"))).describeDenials()).isEqualTo("[]");
    }
}
