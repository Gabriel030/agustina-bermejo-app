package com.red_escuela.backend.shared.domain.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicySetTest {

    private static Policy<Integer> positive() {
        return new Policy<>() {
            public String ruleName() {
                return "positive";
            }

            public PolicyVerdict evaluateRule(Integer value) {
                return value > 0 ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "NOT_POSITIVE", "debe ser positivo");
            }
        };
    }

    private static Policy<Integer> even() {
        return new Policy<>() {
            public String ruleName() {
                return "even";
            }

            public PolicyVerdict evaluateRule(Integer value) {
                return value % 2 == 0 ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "NOT_EVEN", "debe ser par");
            }
        };
    }

    @Test
    void untilFirstDenialStopsAtTheFirstRejectionAndKeepsOrder() {
        PolicySet<Integer> set = PolicySet.of(List.of(positive(), even()));

        PolicyEvaluationResult evaluation = set.evaluateRulesUntilFirstViolation(-3);

        assertThat(evaluation.allowed()).isFalse();
        assertThat(evaluation.verdicts()).extracting(PolicyVerdict::policy).containsExactly("positive");
        assertThat(evaluation.firstDenial()).get().extracting(PolicyVerdict::code).isEqualTo("NOT_POSITIVE");
    }

    @Test
    void evaluateAllCollectsEveryDenial() {
        PolicySet<Integer> set = PolicySet.of(List.of(positive(), even()));

        PolicyEvaluationResult evaluation = set.evaluateAllRules(-3);

        assertThat(evaluation.verdicts()).hasSize(2);
        assertThat(evaluation.denials()).extracting(PolicyVerdict::code).containsExactly("NOT_POSITIVE", "NOT_EVEN");
    }

    @Test
    void anAllowedValuePassesEveryPolicy() {
        PolicyEvaluationResult evaluation = PolicySet.of(List.of(positive(), even())).evaluateRulesUntilFirstViolation(4);

        assertThat(evaluation.allowed()).isTrue();
        assertThat(evaluation.firstDenial()).isEmpty();
        assertThat(evaluation.verdicts()).hasSize(2);
    }

    @Test
    void anEmptySetAllowsEverything() {
        assertThat(PolicySet.<Integer>of(List.of()).evaluateAllRules(1).allowed()).isTrue();
    }

    @Test
    void aDenialWithoutCodeIsRejected() {
        assertThatThrownBy(() -> PolicyVerdict.deny("x", " ", "m")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void combinatorsAllOfAnyOfAndNot() {
        assertThat(Policies.allOf("both", positive(), even()).evaluateRule(3).code()).isEqualTo("NOT_EVEN");
        assertThat(Policies.allOf("both", positive(), even()).evaluateRule(4).allowed()).isTrue();
        assertThat(Policies.anyOf("either", positive(), even()).evaluateRule(-2).allowed()).isTrue();
        assertThat(Policies.anyOf("either", positive(), even()).evaluateRule(-3).code()).isEqualTo("NOT_EVEN");
        assertThat(Policies.not("notEven", "IS_EVEN", "es par", even()).evaluateRule(2).code()).isEqualTo("IS_EVEN");
        assertThat(Policies.not("notEven", "IS_EVEN", "es par", even()).evaluateRule(3).allowed()).isTrue();
    }
}
