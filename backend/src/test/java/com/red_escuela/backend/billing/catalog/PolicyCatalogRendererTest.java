package com.red_escuela.backend.billing.catalog;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-051")
class PolicyCatalogRendererTest {

    @PolicyRule(violationCodes = {"CODE_A", "CODE_B"}, decisions = {"D-001", "D-002"})
    static class DeclaredPolicy implements Policy<String> {
        @Override
        public String name() {
            return "declared";
        }

        @Override
        public PolicyVerdict evaluate(String context) {
            return PolicyVerdict.allow(name());
        }
    }

    static class UndeclaredPolicy implements Policy<String> {
        @Override
        public String name() {
            return "undeclared";
        }

        @Override
        public PolicyVerdict evaluate(String context) {
            return PolicyVerdict.allow(name());
        }
    }

    @Test
    void rendersEachMomentInOrderWithCodesAndDecisions() {
        PolicySet<String> second = PolicySet.named("B_MOMENT", List.of(new DeclaredPolicy(), new UndeclaredPolicy()));
        PolicySet<String> first = PolicySet.named("A_MOMENT", List.of(new DeclaredPolicy()));

        String rendered = PolicyCatalogRenderer.render(List.of(second, first));

        assertThat(rendered.indexOf("## Momento A_MOMENT")).isPositive().isLessThan(rendered.indexOf("## Momento B_MOMENT"));
        assertThat(rendered).contains("| 1 | DeclaredPolicy | CODE_A, CODE_B | D-001, D-002 |")
                .contains("| 2 | UndeclaredPolicy | — | — |");
    }

    @Test
    void rendersTheHeaderWithTheRegenerationCommand() {
        assertThat(PolicyCatalogRenderer.render(List.of()))
                .startsWith("# Catálogo de validaciones de facturación")
                .contains("-Dpolicy-catalog.write=true");
    }
}
