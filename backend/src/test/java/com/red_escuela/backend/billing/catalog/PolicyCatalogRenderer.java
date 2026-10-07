package com.red_escuela.backend.billing.catalog;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicySet;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class PolicyCatalogRenderer {

    private PolicyCatalogRenderer() {
    }

    public static String render(Collection<PolicySet<?>> policySets) {
        return render(policySets, List.of());
    }

    public static String render(Collection<PolicySet<?>> policySets, List<OperationCatalogEntry> operations) {
        StringBuilder out = new StringBuilder();
        out.append("# Catálogo de validaciones de facturación\n\n");
        out.append("Generado por `PolicyCatalogTest`; no se edita a mano. Para regenerarlo: `./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true` y commitear.\n");
        List<PolicySet<?>> sorted = policySets.stream().sorted(Comparator.comparing(PolicySet::moment)).toList();
        for (PolicySet<?> set : sorted) {
            renderSet(out, set);
        }
        operations.stream().sorted(Comparator.comparing(OperationCatalogEntry::type)).forEach(operation -> renderOperation(out, operation));
        return out.toString();
    }

    private static void renderOperation(StringBuilder out, OperationCatalogEntry operation) {
        out.append("\n## Operación ").append(operation.type()).append("\n\n");
        out.append("- Validaciones (momentos): ").append(joinOrDash(operation.validationMoments())).append("\n");
        out.append("- Pre-procesamiento (en orden): ").append(joinOrDash(operation.preHandlers())).append("\n");
        out.append("- Eventos que publica: ").append(joinOrDash(operation.events())).append("\n");
        for (String event : operation.events()) {
            out.append("- Post-procesamiento de ").append(event).append(": ")
                    .append(joinOrDash(operation.listenersByEvent().getOrDefault(event, List.of()))).append("\n");
        }
    }

    private static String joinOrDash(List<String> values) {
        return values.isEmpty() ? "—" : String.join(", ", values);
    }

    private static void renderSet(StringBuilder out, PolicySet<?> set) {
        out.append("\n## Momento ").append(set.moment()).append("\n\n");
        out.append("| Orden | Policy | Códigos de violación | Decisiones |\n|---|---|---|---|\n");
        int order = 1;
        for (Policy<?> policy : set.policies()) {
            PolicyRule rule = policy.getClass().getAnnotation(PolicyRule.class);
            String codes = rule == null ? "—" : String.join(", ", rule.violationCodes());
            String decisions = rule == null ? "—" : String.join(", ", rule.decisions());
            out.append("| ").append(order++).append(" | ").append(policy.getClass().getSimpleName())
                    .append(" | ").append(codes).append(" | ").append(decisions).append(" |\n");
        }
    }
}
