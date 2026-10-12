package com.red_escuela.backend.billing.planning.equivalence;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.domain.YearCycles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

public final class PlanningEquivalenceMatrix {

    public record ProgramScenario(String name, ProgramInvoicePlanningFacts facts, InvoicePlanningScope scope, boolean catchUp) {
    }

    public record CourseScenario(String name, CourseInvoicePlanningFacts facts) {
    }

    private static final YearMonth[] MONTHS = {YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 6), YearMonth.of(2026, 8),
            YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2), YearMonth.of(2027, 3), YearMonth.of(2028, 2), YearMonth.of(2029, 5)};

    private PlanningEquivalenceMatrix() {
    }

    public static List<ProgramScenario> programScenarios() {
        Random random = new Random(20261007L);
        List<ProgramScenario> scenarios = new ArrayList<>();
        for (int i = 0; i < 700; i++) {
            scenarios.add(programScenario(i, random));
        }
        return scenarios;
    }

    private static <T> T pick(Random random, T... values) {
        return values[random.nextInt(values.length)];
    }

    private static ProgramYearBillingTemplate template(long id, int year, boolean dated, int installments, int count, int first, Integer second,
                                                       boolean paysWhilePaused, boolean requireFee, int lead, boolean blocks) {
        Term term = new Term();
        term.setYear(year);
        term.setStartDate(dated ? LocalDateTime.of(year, 3, 1, 0, 0) : null);
        term.setEndDate(dated ? LocalDateTime.of(year, 12, 31, 23, 59) : null);
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setId(id);
        template.setTerm(term);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, installments);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.configureEnrollmentFees(template, count, first, count == 2 ? second : null);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setPaysWhilePaused(template, paysWhilePaused);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRequireEnrollmentFeeForFinalExamOnlyStudents(template, requireFee);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setEnrollmentFeeLeadMonths(template, lead);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksEnrollment(template, blocks);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksExamRegistration(template, blocks);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksCertificates(template, blocks);
        return template;
    }

    private static ProgramScenario programScenario(int index, Random random) {
        boolean dated = random.nextInt(5) != 0;
        int count = pick(random, 1, 2);
        int installments = pick(random, 3, 10);
        boolean paysWhilePaused = random.nextBoolean();
        boolean requireFee = random.nextBoolean();
        int lead = pick(random, 0, 1);
        boolean blocks = random.nextInt(4) != 0;
        int years = pick(random, 1, 2, 3);
        List<ProgramYearBillingTemplate> templates = new ArrayList<>();
        for (int y = 0; y < years; y++) {
            templates.add(template(y + 1, 2026 + (y == 2 ? 2 : y), dated, installments, count, pick(random, 2, 3, 5), pick(random, 6, 8), paysWhilePaused, requireFee, lead, blocks));
        }
        YearMonth month = pick(random, MONTHS);
        LocalDate today = month.atDay(pick(random, 1, 14, 28));
        YearMonth entry = pick(random, null, YearMonth.of(2026, 3), YearMonth.of(2026, 8), YearMonth.of(2027, 3));
        PaymentPlanCoverage coverage = pick(random, PaymentPlanCoverage.none(), PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY),
                PaymentPlanCoverage.ofCadence(PaymentPlanCadence.ANNUAL_UPFRONT),
                new PaymentPlanCoverage(PaymentPlanCadence.ANNUAL_UPFRONT, YearMonth.of(2026, 3), 10),
                new PaymentPlanCoverage(PaymentPlanCadence.ANNUAL_UPFRONT, YearMonth.of(2026, 3), 12));
        YearCycles cycles = new YearCycles(templates.stream().map(t -> com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(t, month.getYear())).toList(), pick(random, null, 1L));
        List<IssuedEnrollmentFee> fees = new ArrayList<>();
        InvoicePaymentStatus feeStatus = pick(random, null, InvoicePaymentStatus.PENDING, InvoicePaymentStatus.PAID, InvoicePaymentStatus.CANCELLED);
        if (feeStatus != null) {
            YearCycle first = cycles.all().get(0);
            fees.add(new IssuedEnrollmentFee(10L, first.templateId(), pick(random, 1, 2), first.slotPeriod(1), feeStatus, first.slotPeriod(1).atDay(1)));
        }
        Set<YearMonth> recurring = new HashSet<>();
        int issued = pick(random, 0, installments - 1, installments);
        for (int k = 0; k < issued; k++) {
            recurring.add(YearMonth.of(2026, 3).plusMonths(k));
        }
        recurring.addAll(pick(random, Set.<YearMonth>of(), Set.of(YearMonth.of(2026, 6)), Set.of(month)));
        Map<String, SlotWindow> windows = new LinkedHashMap<>();
        Supplier<SlotWindow> window = () -> pick(random, null, new SlotWindow(today.plusMonths(1), today.plusMonths(2)),
                new SlotWindow(today.minusDays(10), today.plusDays(10)), new SlotWindow(today.minusDays(20), today),
                new SlotWindow(today.minusMonths(2), today.minusDays(5)));
        for (YearCycle cycle : cycles.all()) {
            for (int slot = 1; slot <= cycle.slotCount(); slot++) {
                SlotWindow w = window.get();
                if (w != null) {
                    windows.put(SlotWindow.key(cycle.templateId(), slot), w);
                }
            }
        }
        boolean cohort = entry != null && random.nextBoolean();
        InvoicePlanningScope scope = pick(random, InvoicePlanningScope.values());
        boolean active = random.nextInt(8) != 0;
        ProgramInvoicePlanningFacts facts = new ProgramInvoicePlanningFacts(1L, month, today, active, random.nextInt(8) == 0, random.nextInt(8) == 0,
                random.nextInt(4) == 0, random.nextInt(4) == 0, entry, coverage, random.nextInt(8) != 0, cycles, fees, recurring, windows,
                cohort ? 1L : null, cohort ? 1 : null, cohort ? entry.atDay(1) : null);
        return new ProgramScenario("program-" + index, facts, scope, random.nextBoolean());
    }

    public static List<CourseScenario> courseScenarios() {
        Random random = new Random(20261008L);
        List<CourseScenario> scenarios = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            YearMonth month = pick(random, MONTHS);
            PaymentPlanCadence cadence = pick(random, null, PaymentPlanCadence.MONTHLY, PaymentPlanCadence.ANNUAL_UPFRONT);
            YearMonth from = pick(random, null, YearMonth.of(2026, 3), YearMonth.of(2026, 11));
            Set<YearMonth> issuedPeriods = pick(random, Set.<YearMonth>of(), Set.of(month), Set.of(YearMonth.of(2026, 4), YearMonth.of(2026, 5)));
            YearMonth termStart = YearMonth.of(2026, pick(random, 3, 5));
            YearMonth termEnd = YearMonth.of(2026, pick(random, 8, 12));
            scenarios.add(new CourseScenario("course-" + i, new CourseInvoicePlanningFacts(1L, month, month.atDay(pick(random, 1, 20)),
                    random.nextInt(8) != 0, random.nextInt(8) == 0, random.nextInt(8) != 0, cadence, from, pick(random, null, 0, 3, 12),
                    period -> !period.isBefore(termStart) && !period.isAfter(termEnd), pick(random, null, 2, 6), issuedPeriods, pick(random, 0L, 1L, 6L), 10)));
        }
        return scenarios;
    }
}
