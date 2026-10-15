package com.red_escuela.backend.billing.decision;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-170")
class BillingDecisionCoverageTest {

    @Test
    void everyImplementedDecisionHasATaggedTest() {
        DecisionRegistry registry = DecisionRegistry.readFile(Path.of("../docs/billing/decision-registry.md"));
        List<TaggedTestUse> uses = TaggedTestScanner.scanDirectory(Path.of("src/test/java"));

        assertThat(DecisionCoverageRules.findViolations(registry, uses)).isEmpty();
    }
}
