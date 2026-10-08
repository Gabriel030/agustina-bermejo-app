package com.red_escuela.backend.billing.planning.equivalence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.service.CourseMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-050")
@Tag("D-051")
class PlanningEquivalenceGoldenTest {
    private static final Path GOLDEN = Path.of("src/test/resources/billing/planning/planning-equivalence-golden.json");
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);

    private final ProgramMonthlyInvoicePlanner programPlanner = new ProgramMonthlyInvoicePlanner();
    private final CourseMonthlyInvoicePlanner coursePlanner = new CourseMonthlyInvoicePlanner();

    private Map<String, Object> currentBehaviour() {
        Map<String, Object> behaviour = new LinkedHashMap<>();
        for (PlanningEquivalenceMatrix.ProgramScenario s : PlanningEquivalenceMatrix.programScenarios()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("plan", programPlanner.planInvoicesForMonth(s.facts(), s.scope(), s.catchUp()));
            entry.put("requirement", PlanningOutcomes.requirementOutcomes(s.facts()));
            behaviour.put(s.name(), entry);
        }
        for (PlanningEquivalenceMatrix.CourseScenario s : PlanningEquivalenceMatrix.courseScenarios()) {
            behaviour.put(s.name(), Map.of("plan", coursePlanner.planInvoicesForMonth(s.facts())));
        }
        return behaviour;
    }

    @Test
    void thePlannersStillDecideWhatTheReferenceFileRecords() throws IOException {
        String current = MAPPER.writeValueAsString(currentBehaviour());
        if (Boolean.getBoolean("planning-golden.write")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, current);
        }

        assertThat(GOLDEN).as("run ./mvnw test -Dtest=PlanningEquivalenceGoldenTest -Dplanning-golden.write=true and justify the JSON diff in the PR").exists();
        assertThat(MAPPER.readTree(Files.readString(GOLDEN))).isEqualTo(MAPPER.readTree(current));
    }

    @Test
    @Tag("D-001")
    void theOnlyIntentionalDifferenceFromTheLegacyPlannerIsThatUndatedTermsNoLongerBillJanuaryAndFebruary() {
        PlanningEquivalenceMatrix.ProgramScenario january = PlanningEquivalenceMatrix.programScenarios().stream()
                .filter(s -> s.facts().cycles().all().stream().anyMatch(c -> c.template().getTerm().getStartDate() == null))
                .filter(s -> s.facts().month().getMonthValue() <= 2).findFirst().orElseThrow();
        ProgramInvoicePlanningFacts facts = january.facts();

        MonthlyInvoicePlan plan = programPlanner.planInvoicesForMonth(facts, january.scope(), january.catchUp());

        assertThat(plan.charges()).noneMatch(c -> c.kind() == com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT
                && c.period().equals(YearMonth.from(facts.month().atDay(1))));
        assertThat(plan.decisions()).noneMatch(d -> d.code().equals("ISSUE_INSTALLMENT"));
    }
}
