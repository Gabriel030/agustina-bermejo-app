package com.red_escuela.backend.billing.catalog;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PolicyCatalogRenderer {

    private PolicyCatalogRenderer() {
    }

    public static String render(Collection<PolicySet<?>> policySets) {
        return render(policySets, Map.of(), List.of());
    }

    public static String render(Collection<PolicySet<?>> policySets, Map<String, StateTransitionTable<?, ?>> machines) {
        return render(policySets, machines, List.of());
    }

    public static String render(Collection<PolicySet<?>> policySets, Map<String, StateTransitionTable<?, ?>> machines, List<OperationCatalogEntry> operations) {
        StringBuilder out = new StringBuilder();
        out.append("# Catálogo de validaciones de facturación\n\n");
        out.append("Generado por `PolicyCatalogTest`; no se edita a mano. Para regenerarlo: `./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true` y commitear.\n");
        List<PolicySet<?>> sorted = policySets.stream().sorted(Comparator.comparing(PolicySet::moment)).toList();
        for (PolicySet<?> set : sorted) {
            renderSet(out, set);
        }
        if (!machines.isEmpty()) {
            out.append("\n## Máquinas de estado\n");
            new java.util.TreeMap<>(machines).forEach((name, table) -> renderMachine(out, name.substring(name.lastIndexOf('.') + 1), table));
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

    private static void renderMachine(StringBuilder out, String name, StateTransitionTable<?, ?> table) {
        List<Enum<?>> states = new ArrayList<>();
        states.add(null);
        states.addAll(List.of(table.stateType().getEnumConstants()));
        Object[] events = table.eventType().getEnumConstants();
        out.append("\n### ").append(name).append("\n\n| Desde \\ Evento |");
        for (Object event : events) {
            out.append(' ').append(((Enum<?>) event).name()).append(" |");
        }
        out.append("\n|---|").append("---|".repeat(events.length)).append('\n');
        List<String> invalid = new ArrayList<>();
        for (Enum<?> state : states) {
            String from = state == null ? "(sin estado)" : state.name();
            out.append("| ").append(from).append(" |");
            for (Object event : events) {
                var target = findTarget(table, state, (Enum<?>) event);
                out.append(' ').append(target == null ? "—" : target.name()).append(" |");
                if (target == null) {
                    invalid.add(from + " × " + ((Enum<?>) event).name());
                }
            }
            out.append('\n');
        }
        out.append("\nTransiciones válidas: ").append(table.listAllowedTransitions().size()).append(" de ")
                .append(states.size() * events.length).append(". Pares inválidos: ").append(String.join("; ", invalid)).append(".\n");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> findTarget(StateTransitionTable table, Enum<?> state, Enum<?> event) {
        return (Enum<?>) table.findTargetStateFor(state, event).orElse(null);
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
