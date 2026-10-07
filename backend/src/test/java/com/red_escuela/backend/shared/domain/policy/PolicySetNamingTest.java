package com.red_escuela.backend.shared.domain.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-051")
class PolicySetNamingTest {

    @Test
    void aNamedSetExposesItsMomentAndItsOrderedPolicies() {
        Policy<String> policy = new Policy<>() {
            @Override
            public String name() {
                return "p";
            }

            @Override
            public PolicyVerdict evaluate(String context) {
                return PolicyVerdict.allow("p");
            }
        };

        PolicySet<String> set = PolicySet.named("MOMENT", List.of(policy));

        assertThat(set.moment()).isEqualTo("MOMENT");
        assertThat(set.policies()).containsExactly(policy);
        assertThat(PolicySet.of(List.<Policy<String>>of()).moment()).isEqualTo("UNNAMED");
    }

    @Test
    void aNamedSetRequiresANonBlankMoment() {
        assertThatThrownBy(() -> PolicySet.named(" ", List.<Policy<String>>of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PolicySet.named(null, List.<Policy<String>>of())).isInstanceOf(NullPointerException.class);
    }
}
