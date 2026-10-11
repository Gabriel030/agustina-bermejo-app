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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-330")
@Tag("D-331")
@Tag("D-332")
@Tag("D-288")
@Tag("D-293")
@Tag("D-294")
class LateFeeAccrualProperties {
    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final StudentSnapshot RECIPIENT = new StudentSnapshot("Ana Perez", "30111222", null);
    private static final NonNegativeMoney BASE = NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS);
    private static final PolicySet<LateFeeIssuingContext> POLICIES = new CollectionsPolicySetsConfiguration().debitNoteIssuingPolicySet();
    private static final DailySimpleLateFeeCalculator CALCULATOR = new DailySimpleLateFeeCalculator();
    private static final LateFeeAccrualPlanner PLANNER = new LateFeeAccrualPlanner();

    enum Kind { ADVANCE, CHANGE_VERSION, TOGGLE_ENABLED, TOGGLE_FREEZE, END_ENROLLMENT, ENTER_REVIEW, LEAVE_REVIEW }

    record Step(Kind kind, int value) {
    }

    record Scenario(int dueOffsetDays, int graceDays, List<Step> steps) {
    }

    record Run(LocalDate date, boolean underReview, Optional<LocalDate> endDate) {
    }

    record Issued(LateFeeAccrualPeriod period, int chargedDays, LocalDate issuedOn, NonNegativeMoney amount) {
    }

    record Outcome(LocalDate dueDate, List<Issued> issued, List<Run> runs, TreeMap<LocalDate, LatePaymentTerms> timeline, LatePaymentTerms dueDateTerms) {
        LatePaymentTerms termsOn(LocalDate date) {
            return timeline.floorEntry(date).getValue();
        }
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Step> step = Combinators.combine(Arbitraries.of(Kind.values()), Arbitraries.integers().between(1, 45)).as(Step::new);
        return Combinators.combine(Arbitraries.integers().between(0, 200), Arbitraries.integers().between(0, 15), step.list().ofMinSize(1).ofMaxSize(40)).as(Scenario::new);
    }

    private static LatePaymentTerms terms(int grace, int ratePoints, boolean enabled, boolean freeze) {
        return new LatePaymentTerms(grace, Percentage.ofPoints(new BigDecimal(ratePoints).movePointLeft(2)), 30, 1, true, 10, freeze, enabled);
    }

    private static Outcome simulate(int dueOffsetDays, int graceDays, List<Step> steps) {
        LocalDate dueDate = START.plusDays(dueOffsetDays);
        DailyLateFeeSchedule schedule = new DailyLateFeeSchedule(dueDate, graceDays);
        TreeMap<LocalDate, LatePaymentTerms> timeline = new TreeMap<>();
        LatePaymentTerms initial = terms(graceDays, 20, true, true);
        timeline.put(LocalDate.MIN, initial);
        LocalDate today = dueDate;
        Optional<LocalDate> end = Optional.empty();
        boolean underReview = false;
        List<LateFeeAccrualPeriod> covered = new ArrayList<>();
        List<Issued> issued = new ArrayList<>();
        List<Run> runs = new ArrayList<>();
        for (Step step : steps) {
            LatePaymentTerms current = timeline.lastEntry().getValue();
            switch (step.kind()) {
                case ADVANCE -> {
                    today = today.plusDays(step.value());
                    runs.add(new Run(today, underReview, end));
                    runSweep(schedule, dueDate, initial, timeline, covered, issued, today, underReview, end);
                }
                case CHANGE_VERSION -> timeline.put(today.plusDays(1), terms(step.value() % 16, step.value() % 50 + 1, current.lateFeeChargingEnabled(), current.freezeLateFeesOnWithdrawal()));
                case TOGGLE_ENABLED -> timeline.put(today.plusDays(1), new LatePaymentTerms(current.gracePeriodDays(), current.dailyLateFeeRate(), 30, 1, true, 10,
                        current.freezeLateFeesOnWithdrawal(), !current.lateFeeChargingEnabled()));
                case TOGGLE_FREEZE -> timeline.put(today.plusDays(1), new LatePaymentTerms(current.gracePeriodDays(), current.dailyLateFeeRate(), 30, 1, true, 10,
                        !current.freezeLateFeesOnWithdrawal(), current.lateFeeChargingEnabled()));
                case END_ENROLLMENT -> end = end.isPresent() ? end : Optional.of(today);
                case ENTER_REVIEW -> underReview = true;
                case LEAVE_REVIEW -> underReview = false;
            }
        }
        return new Outcome(dueDate, issued, runs, timeline, initial);
    }

    private static void runSweep(DailyLateFeeSchedule schedule, LocalDate dueDate, LatePaymentTerms dueTerms, TreeMap<LocalDate, LatePaymentTerms> timeline,
                                 List<LateFeeAccrualPeriod> covered, List<Issued> issued, LocalDate today, boolean underReview, Optional<LocalDate> end) {
        if (!schedule.isPastGraceOn(today)) {
            return;
        }
        LateFeeCutoff cutoff = LateFeeCutoff.closingOfMonthBefore(today);
        LatePaymentTerms cutoffTerms = timeline.floorEntry(cutoff.lastDay()).getValue();
        Optional<LocalDate> frozenAfter = cutoffTerms.freezeLateFeesOnWithdrawal() ? end : Optional.empty();
        List<PlannedLateFeePeriod> planned = PLANNER.planMissingPeriods(schedule, covered, cutoff, day -> timeline.floorEntry(day).getValue().lateFeeChargingEnabled(), frozenAfter);
        LateFeeOriginFacts origin = new LateFeeOriginFacts(1L, "F-00000001", 7L, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT,
                underReview ? InvoicePaymentStatus.PAYMENT_UNDER_REVIEW : InvoicePaymentStatus.OVERDUE, dueDate, Currency.ARS, BASE, RECIPIENT, YearMonth.from(dueDate), 3L, null);
        for (PlannedLateFeePeriod period : planned) {
            LateFeeIssuingContext context = new LateFeeIssuingContext(origin, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin), period, 9L, dueTerms, cutoffTerms, end,
                    CALCULATOR.calculateLateFee(BASE, dueTerms.dailyLateFeeRate(), period.chargedDays()), today, Optional.empty());
            if (POLICIES.evaluateRulesUntilFirstViolation(context).allowed() && !context.lateFeeAmount().value().isZero()) {
                covered.add(period.period());
                issued.add(new Issued(period.period(), period.chargedDays(), today, context.lateFeeAmount()));
            }
        }
    }

    private static Set<LocalDate> oracleChargedDays(Outcome outcome, int graceDays) {
        Set<LocalDate> charged = new HashSet<>();
        for (Run run : outcome.runs()) {
            LocalDate cutoffDay = run.date().withDayOfMonth(1).minusDays(1);
            boolean pastGrace = run.date().isAfter(outcome.dueDate().plusDays(graceDays));
            if (!pastGrace || run.underReview()) {
                continue;
            }
            boolean freeze = outcome.termsOn(cutoffDay).freezeLateFeesOnWithdrawal();
            for (LocalDate day = outcome.dueDate().plusDays(1); !day.isAfter(cutoffDay); day = day.plusDays(1)) {
                boolean frozen = freeze && run.endDate().isPresent() && day.isAfter(run.endDate().get());
                if (!charged.contains(day) && outcome.termsOn(day).lateFeeChargingEnabled() && !frozen) {
                    charged.add(day);
                }
            }
        }
        return charged;
    }

    @Property(tries = 1000, seed = "20261010")
    void plannerMatchesTheDayByDayOracleWithoutPayments(@ForAll("scenarios") Scenario scenario) {
        Outcome outcome = simulate(scenario.dueOffsetDays(), scenario.graceDays(), scenario.steps());

        Set<LocalDate> seen = new HashSet<>();
        Set<LocalDate> expandedEnabledDays = new HashSet<>();
        for (Issued entry : outcome.issued()) {
            assertThat(entry.period().firstDay()).isAfter(outcome.dueDate());
            assertThat(entry.issuedOn()).isAfter(entry.period().lastDay());
            int enabled = 0;
            for (LocalDate day = entry.period().firstDay(); !day.isAfter(entry.period().lastDay()); day = day.plusDays(1)) {
                assertThat(seen.add(day)).as("day %s covered twice", day).isTrue();
                if (outcome.termsOn(day).lateFeeChargingEnabled()) {
                    enabled++;
                    expandedEnabledDays.add(day);
                }
            }
            assertThat(entry.chargedDays()).isEqualTo(enabled);
            BigDecimal expectedAmount = BASE.value().amount().multiply(outcome.dueDateTerms().dailyLateFeeRate().fraction()).multiply(BigDecimal.valueOf(entry.chargedDays()))
                    .setScale(2, RoundingMode.HALF_UP);
            assertThat(entry.amount().value().amount()).isEqualByComparingTo(expectedAmount);
        }
        assertThat(expandedEnabledDays).isEqualTo(oracleChargedDays(outcome, scenario.graceDays()));
    }

    @Property(tries = 500, seed = "20261011")
    void runningLessOftenNeverChangesWhichPeriodsAreIssuedWhenNothingElseHappens(@ForAll("scenarios") Scenario scenario) {
        int total = scenario.steps().stream().mapToInt(Step::value).sum();
        List<Step> everyDay = new ArrayList<>();
        for (int day = 0; day < total; day++) {
            everyDay.add(new Step(Kind.ADVANCE, 1));
        }
        List<Step> once = List.of(new Step(Kind.ADVANCE, total));

        Outcome daily = simulate(scenario.dueOffsetDays(), scenario.graceDays(), everyDay);
        Outcome single = simulate(scenario.dueOffsetDays(), scenario.graceDays(), once);

        assertThat(single.issued()).extracting(Issued::period).containsExactlyElementsOf(daily.issued().stream().map(Issued::period).toList());
        assertThat(single.issued()).extracting(Issued::amount).containsExactlyElementsOf(daily.issued().stream().map(Issued::amount).toList());
    }
}
