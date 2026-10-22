package com.red_escuela.backend.billing.decision;

import java.util.ArrayList;
import java.util.List;

public final class DecisionCoverageRules {

    private static final String CONTRADICTS = "contradicts-";

    private DecisionCoverageRules() {
    }

    public static List<String> findViolations(DecisionRegistry registry, List<TaggedTestUse> uses) {
        List<String> violations = new ArrayList<>();
        for (DecisionRegistryRow row : registry.rows()) {
            if (row.isCurrent() && row.isImplementedOrPartial()) {
                checkCoverage(row, uses, violations);
            }
        }
        for (TaggedTestUse use : uses) {
            checkUse(registry, use, violations);
        }
        return violations;
    }

    private static void checkCoverage(DecisionRegistryRow row, List<TaggedTestUse> uses, List<String> violations) {
        if (!hasTag(uses, row.id())) {
            violations.add("R1 " + row.id() + " (" + row.area() + ") sin test: " + row.testCase());
        } else if (!row.isToConfirm() && !hasPrTierTest(uses, row.id())) {
            violations.add("R5 " + row.id() + " (" + row.area() + ") sin test del tier PR (solo nightly, migration o deshabilitados): " + row.testCase());
        }
    }

    private static boolean hasPrTierTest(List<TaggedTestUse> uses, String id) {
        return uses.stream().anyMatch(use -> use.tag().equals(id) && !use.disabled() && !use.outsidePrTier());
    }

    private static boolean hasTag(List<TaggedTestUse> uses, String id) {
        return uses.stream().anyMatch(use -> use.tag().equals(id));
    }

    private static void checkUse(DecisionRegistry registry, TaggedTestUse use, List<String> violations) {
        if (use.tag().startsWith(CONTRADICTS)) {
            checkContradicts(registry, use, violations);
            return;
        }
        var row = registry.find(use.tag());
        if (row.isEmpty() || !row.get().isCurrent()) {
            violations.add("R2 " + use.tag() + " in " + use.source() + " cites a missing or superseded decision");
            return;
        }
        if (row.get().isToConfirm() && !use.disabled()) {
            violations.add("R4 " + use.tag() + " in " + use.source() + " is active but the decision is pending confirmation");
        }
    }

    private static void checkContradicts(DecisionRegistry registry, TaggedTestUse use, List<String> violations) {
        String id = use.tag().substring(CONTRADICTS.length());
        var row = registry.find(id);
        if (row.isEmpty() || !row.get().isViolated()) {
            violations.add("R3 " + use.tag() + " in " + use.source() + " but " + id + " is not marked violada");
        }
    }
}
