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
        public String ruleName() {
            return "declared";
        }

        @Override
        public PolicyVerdict evaluateRule(String context) {
            return PolicyVerdict.allow(name());
        }
    }

    static class UndeclaredPolicy implements Policy<String> {
        @Override
        public String ruleName() {
            return "undeclared";
        }

        @Override
        public PolicyVerdict evaluateRule(String context) {
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

    @Test
    void rendersEachOperationWithItsHandlersEventsAndListeners() {
        OperationCatalogEntry withHandlers = new OperationCatalogEntry("B_OPERATION", List.of("MOMENT_X", "MOMENT_Y"), List.of("FirstHandler", "SecondHandler"),
                List.of("DoneEvent"), java.util.Map.of("DoneEvent", List.of("ListenerA#on", "ListenerB#on")));
        OperationCatalogEntry bare = new OperationCatalogEntry("A_OPERATION", List.of(), List.of(), List.of("OtherEvent"), java.util.Map.of());

        String rendered = PolicyCatalogRenderer.render(List.of(), java.util.Map.of(), List.of(withHandlers, bare));

        assertThat(rendered.indexOf("## Operación A_OPERATION")).isPositive().isLessThan(rendered.indexOf("## Operación B_OPERATION"));
        assertThat(rendered).contains("- Validaciones (momentos): MOMENT_X, MOMENT_Y")
                .contains("- Pre-procesamiento (en orden): FirstHandler, SecondHandler")
                .contains("- Post-procesamiento de DoneEvent: ListenerA#on, ListenerB#on")
                .contains("- Validaciones (momentos): —")
                .contains("- Post-procesamiento de OtherEvent: —");
    }
}
