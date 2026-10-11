package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.collections.CollectionsPolicySetsConfiguration;
import com.red_escuela.backend.billing.collections.policy.LateFeeDebitNoteDraftFacts;
import com.red_escuela.backend.billing.collections.policy.LateFeeIssuingContext;
import com.red_escuela.backend.billing.collections.policy.LateFeeOriginFacts;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-239")
@Tag("D-293")
@Tag("D-294")
@Tag("D-288")
class LateFeeAccrualProperties {
    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final StudentSnapshot RECIPIENT = new StudentSnapshot("Ana Perez", "30111222", null);
    private static final NonNegativeMoney BASE = NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS);
    private static final PolicySet<LateFeeIssuingContext> POLICIES = new CollectionsPolicySetsConfiguration().debitNoteIssuingPolicySet();
    private static final MonthlySimpleLateFeeCalculator CALCULATOR = new MonthlySimpleLateFeeCalculator();

    enum Kind { ADVANCE, CHANGE_RATE, TOGGLE_ENABLED, TOGGLE_FREEZE, END_ENROLLMENT, ENTER_REVIEW, LEAVE_REVIEW }

    record Step(Kind kind, int value) {
    }

    record Scenario(int dueOffsetDays, int graceDays, List<Step> steps) {
    }

    record Issued(int ordinal, LocalDate factDate, LocalDate issuedOn, NonNegativeMoney amount, boolean underReviewWhenIssued) {
    }

    record Outcome(LocalDate dueDate, Map<Integer, Issued> issued, List<Issued> issuedInOrder, Optional<LocalDate> endDate, Map<LocalDate, LatePaymentTerms> timeline,
                   LatePaymentTerms dueDateTerms) {
        LatePaymentTerms termsOn(LocalDate date) {
            return timeline.entrySet().stream().filter(entry -> !entry.getKey().isAfter(date)).reduce((first, second) -> second).orElseThrow().getValue();
        }
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Step> step = Combinators.combine(Arbitraries.of(Kind.values()), Arbitraries.integers().between(1, 45)).as(Step::new);
        return Combinators.combine(Arbitraries.integers().between(0, 200), Arbitraries.integers().between(0, 15), step.list().ofMinSize(1).ofMaxSize(40)).as(Scenario::new);
    }

    private static LatePaymentTerms terms(int points, boolean enabled, boolean freeze) {
        return new LatePaymentTerms(5, Percentage.ofPoints(new BigDecimal(points)), 30, 1, true, 10, freeze, enabled);
    }

    private static Outcome simulate(int dueOffsetDays, int graceDays, List<Step> steps) {
        LocalDate dueDate = START.plusDays(dueOffsetDays);
        LateFeeSchedule schedule = new LateFeeSchedule(dueDate, graceDays);
        TreeMap<LocalDate, LatePaymentTerms> timeline = new TreeMap<>();
        LatePaymentTerms initial = terms(5, true, true);
        timeline.put(LocalDate.MIN, initial);
        LocalDate today = dueDate;
        Optional<LocalDate> end = Optional.empty();
        boolean underReview = false;
        Map<Integer, Issued> issued = new TreeMap<>();
        List<Issued> inOrder = new ArrayList<>();
        for (Step step : steps) {
            LatePaymentTerms current = timeline.lastEntry().getValue();
            switch (step.kind()) {
                case ADVANCE -> {
                    today = today.plusDays(step.value());
                    for (LateFeeMonth month : schedule.monthsWithFactDateOnOrBefore(today)) {
                        if (issued.containsKey(month.ordinal())) {
                            continue;
                        }
                        LateFeeOriginFacts origin = new LateFeeOriginFacts(1L, "F-00000001", 7L, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT,
                                underReview ? InvoicePaymentStatus.PAYMENT_UNDER_REVIEW : InvoicePaymentStatus.OVERDUE, dueDate, Currency.ARS, BASE, RECIPIENT, YearMonth.from(dueDate), 3L, null);
                        LatePaymentTerms factDateTerms = timeline.floorEntry(month.factDate()).getValue();
                        LateFeeIssuingContext context = new LateFeeIssuingContext(origin, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin), month, 9L, initial, factDateTerms,
                                end, CALCULATOR.calculateLateFee(BASE, initial.monthlyLateFeeRate()), today);
                        if (POLICIES.evaluateRulesUntilFirstViolation(context).allowed()) {
                            Issued entry = new Issued(month.ordinal(), month.factDate(), today, context.lateFeeAmount(), underReview);
                            issued.put(month.ordinal(), entry);
                            inOrder.add(entry);
                        }
                    }
                }
                case CHANGE_RATE -> timeline.put(today.plusDays(1), terms(step.value() % 10 + 1, current.lateFeeChargingEnabled(), current.freezeLateFeesOnWithdrawal()));
                case TOGGLE_ENABLED -> timeline.put(today.plusDays(1), terms(current.monthlyLateFeeRate().points().intValue(), !current.lateFeeChargingEnabled(), current.freezeLateFeesOnWithdrawal()));
                case TOGGLE_FREEZE -> timeline.put(today.plusDays(1), terms(current.monthlyLateFeeRate().points().intValue(), current.lateFeeChargingEnabled(), !current.freezeLateFeesOnWithdrawal()));
                case END_ENROLLMENT -> end = end.isPresent() ? end : Optional.of(today);
                case ENTER_REVIEW -> underReview = true;
                case LEAVE_REVIEW -> underReview = false;
            }
        }
        return new Outcome(dueDate, issued, inOrder, end, timeline, initial);
    }

    @Property(tries = 1000, seed = "20261010")
    void everyRandomSequenceKeepsTheLateFeeInvariantOfTheProductionPolicies(@ForAll("scenarios") Scenario scenario) {
        LateFeeSchedule schedule = new LateFeeSchedule(START.plusDays(scenario.dueOffsetDays()), scenario.graceDays());
        Outcome outcome = simulate(scenario.dueOffsetDays(), scenario.graceDays(), scenario.steps());

        assertThat(outcome.issuedInOrder()).extracting(Issued::ordinal).doesNotHaveDuplicates();
        for (Issued entry : outcome.issuedInOrder()) {
            assertThat(entry.factDate()).isEqualTo(schedule.lateFeeMonth(entry.ordinal()).factDate());
            assertThat(entry.factDate()).isAfter(outcome.dueDate());
            assertThat(entry.issuedOn()).isAfterOrEqualTo(entry.factDate());
            assertThat(entry.amount()).isEqualTo(CALCULATOR.calculateLateFee(BASE, outcome.dueDateTerms().monthlyLateFeeRate()));
            LatePaymentTerms onFactDate = outcome.termsOn(entry.factDate());
            assertThat(onFactDate.lateFeeChargingEnabled()).as("enabled on fact date %s", entry.factDate()).isTrue();
            assertThat(entry.underReviewWhenIssued()).isFalse();
            if (onFactDate.freezeLateFeesOnWithdrawal() && outcome.endDate().isPresent()) {
                assertThat(entry.factDate()).isBeforeOrEqualTo(outcome.endDate().get());
            }
        }
    }

    @Property(tries = 500, seed = "20261011")
    void runningLessOftenNeverChangesWhichMonthsAccrueWhenNothingElseHappens(@ForAll("scenarios") Scenario scenario) {
        int total = scenario.steps().stream().mapToInt(Step::value).sum();
        List<Step> everyDay = new ArrayList<>();
        for (int day = 0; day < total; day++) {
            everyDay.add(new Step(Kind.ADVANCE, 1));
        }
        List<Step> once = List.of(new Step(Kind.ADVANCE, total));

        Outcome daily = simulate(scenario.dueOffsetDays(), scenario.graceDays(), everyDay);
        Outcome single = simulate(scenario.dueOffsetDays(), scenario.graceDays(), once);

        assertThat(single.issued().keySet()).containsExactlyElementsOf(daily.issued().keySet());
        single.issued().forEach((ordinal, entry) -> assertThat(entry.factDate()).isEqualTo(daily.issued().get(ordinal).factDate()));
    }
}
