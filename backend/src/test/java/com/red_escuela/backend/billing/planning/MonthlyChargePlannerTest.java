package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyChargePlannerTest {
    private final MonthlyChargePlanner planner = new MonthlyChargePlanner();

    private static ProgramYearBillingTemplate template(long id, int year, int installments, int count, int first, Integer second) {
        Term term = new Term();
        term.setYear(year);
        term.setStartDate(LocalDateTime.of(year, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(year, 12, 31, 23, 59));
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setId(id);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(installments);
        template.configureEnrollmentFees(count, first, second);
        return template;
    }

    private record Builder(boolean active, boolean suspended, boolean completed, boolean onlyFinals, YearMonth entry, PaymentPlanCadence cadence,
                           boolean hasPlan, List<ProgramYearBillingTemplate> templates, Set<YearMonth> fees, Set<YearMonth> installments,
                           Map<String, SlotWindow> windows, boolean paused) {
        static Builder base(ProgramYearBillingTemplate... templates) {
            return new Builder(true, false, false, false, null, PaymentPlanCadence.MONTHLY, true, List.of(templates), Set.of(), Set.of(), Map.of(), false);
        }

        Builder inactive() {
            return new Builder(false, suspended, completed, onlyFinals, entry, cadence, hasPlan, templates, fees, installments, windows, paused);
        }

        Builder isSuspended() {
            return new Builder(active, true, completed, onlyFinals, entry, cadence, hasPlan, templates, fees, installments, windows, paused);
        }

        Builder isCompleted() {
            return new Builder(active, suspended, true, onlyFinals, entry, cadence, hasPlan, templates, fees, installments, windows, paused);
        }

        Builder isOnlyFinals() {
            return new Builder(active, suspended, completed, true, entry, cadence, hasPlan, templates, fees, installments, windows, paused);
        }

        Builder entry(YearMonth month) {
            return new Builder(active, suspended, completed, onlyFinals, month, cadence, hasPlan, templates, fees, installments, windows, paused);
        }

        Builder cadence(PaymentPlanCadence value) {
            return new Builder(active, suspended, completed, onlyFinals, entry, value, hasPlan, templates, fees, installments, windows, paused);
        }

        Builder noPlan() {
            return new Builder(active, suspended, completed, onlyFinals, entry, null, false, templates, fees, installments, windows, paused);
        }

        Builder fees(YearMonth... periods) {
            return new Builder(active, suspended, completed, onlyFinals, entry, cadence, hasPlan, templates, Set.of(periods), installments, windows, paused);
        }

        Builder installments(YearMonth... periods) {
            return new Builder(active, suspended, completed, onlyFinals, entry, cadence, hasPlan, templates, fees, Set.of(periods), windows, paused);
        }

        Builder window(long templateId, int slot, LocalDate opens, LocalDate closes) {
            return new Builder(active, suspended, completed, onlyFinals, entry, cadence, hasPlan, templates, fees, installments,
                    Map.of(SlotWindow.key(templateId, slot), new SlotWindow(opens, closes)), paused);
        }

        Builder isPaused() {
            return new Builder(active, suspended, completed, onlyFinals, entry, cadence, hasPlan, templates, fees, installments, windows, true);
        }

        ChargePlanningFacts at(YearMonth month, LocalDate today) {
            YearCycles cycles = new YearCycles(templates.stream().map(t -> YearCycle.of(t, month.getYear())).toList(), null);
            return new ChargePlanningFacts(1L, month, today, active, suspended, completed, paused, onlyFinals, entry, cadence, hasPlan, cycles,
                    fees.stream().map(p -> new IssuedEnrollmentFee(null, null, null, p, InvoiceStatus.PENDING, p.atDay(1))).toList(), installments, windows);
        }
    }

    private ChargePlan plan(Builder builder, YearMonth month) {
        return planner.plan(builder.at(month, month.atDay(1)), ChargeScope.ALL);
    }

    private static YearMonth ym(int year, int month) {
        return YearMonth.of(year, month);
    }

    @Test
    void question1StopsEverythingForIneligibleEnrollmentsWithAReason() {
        ProgramYearBillingTemplate t = template(1, 2026, 10, 1, 2, null);
        assertThat(plan(Builder.base(t).inactive(), ym(2026, 3)).charges()).isEmpty();
        assertThat(plan(Builder.base(t).isSuspended(), ym(2026, 3)).decisions()).extracting(PlanDecision::code).containsExactly("SUSPENDED_OR_WITHDRAWN");
        assertThat(plan(Builder.base(t).isCompleted(), ym(2026, 3)).decisions()).extracting(PlanDecision::code).containsExactly("PLAN_COMPLETED");
        assertThat(plan(Builder.base(t).noPlan(), ym(2026, 3)).decisions()).extracting(PlanDecision::code).containsExactly("NO_PAYMENT_PLAN");
        assertThat(plan(Builder.base(t).inactive(), ym(2026, 3)).decisions()).extracting(PlanDecision::code).containsExactly("NOT_ACTIVE");
    }

    @Test
    void aMonthCanProduceTheInstallmentAndTheEnrollmentFeeTogether() {
        ChargePlan plan = plan(Builder.base(template(1, 2026, 10, 1, 3, null)), ym(2026, 3));

        assertThat(plan.charges()).extracting(PlannedCharge::kind).containsExactlyInAnyOrder(ChargeKind.ENROLLMENT_FEE, ChargeKind.RECURRING_FEE);
        assertThat(plan.charges()).allMatch(c -> c.reason() != null && !c.reason().isBlank());
    }

    @Test
    void enrollmentFeeOutsideTheInstallmentRangeIsAloneAndTheMonthBeforeTheTermHasNoInstallment() {
        ChargePlan plan = plan(Builder.base(template(1, 2026, 10, 1, 2, null)), ym(2026, 2));

        assertThat(plan.charges()).extracting(PlannedCharge::kind).containsExactly(ChargeKind.ENROLLMENT_FEE);
        assertThat(plan.charges().get(0).period()).isEqualTo(ym(2026, 2));
        assertThat(plan.charges().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void installmentCapAndIdempotencyAreReportedWithTheirOwnCode() {
        ProgramYearBillingTemplate t = template(1, 2026, 2, 1, 2, null);
        ChargePlan capped = plan(Builder.base(t).fees(ym(2026, 2)).installments(ym(2026, 3), ym(2026, 4)), ym(2026, 5));
        ChargePlan repeated = plan(Builder.base(t).fees(ym(2026, 2)).installments(ym(2026, 3)), ym(2026, 3));

        assertThat(capped.charges()).isEmpty();
        assertThat(capped.decisions()).extracting(PlanDecision::code).contains("INSTALLMENT_CAP_REACHED");
        assertThat(repeated.charges()).isEmpty();
        assertThat(repeated.decisions()).extracting(PlanDecision::code).contains("ALREADY_ISSUED");
    }

    @Test
    void onlyFinalsSuppressesInstallmentsAndEnrollmentFeesUnlessTheYearRequiresThem() {
        ProgramYearBillingTemplate t = template(1, 2026, 10, 1, 3, null);
        assertThat(plan(Builder.base(t).isOnlyFinals(), ym(2026, 3)).charges()).isEmpty();

        t.setRequireEnrollmentFeeForFinalExamOnlyStudents(true);
        assertThat(plan(Builder.base(t).isOnlyFinals(), ym(2026, 3)).charges()).extracting(PlannedCharge::kind).containsExactly(ChargeKind.ENROLLMENT_FEE);
    }

    @Test
    void upfrontStudentsOnlyReceiveTheEnrollmentFee() {
        ChargePlan plan = plan(Builder.base(template(1, 2026, 10, 1, 3, null)).cadence(PaymentPlanCadence.UPFRONT), ym(2026, 3));

        assertThat(plan.charges()).extracting(PlannedCharge::kind).containsExactly(ChargeKind.ENROLLMENT_FEE);
        assertThat(plan.decisions()).extracting(PlanDecision::code).contains("PLAN_NOT_MONTHLY");
    }

    @Test
    void theEntryFeeInsideTheSlotCountsAndASlotEndedBeforeTheEntryIsNotCharged() {
        ProgramYearBillingTemplate t = template(1, 2026, 10, 2, 2, 7);
        Builder augustEntry = Builder.base(t).entry(ym(2026, 8)).fees(ym(2026, 8));

        assertThat(plan(augustEntry, ym(2026, 2)).charges()).isEmpty();
        assertThat(plan(augustEntry, ym(2026, 7)).charges()).isEmpty();
        assertThat(plan(Builder.base(t).entry(ym(2026, 3)).fees(ym(2026, 3)), ym(2026, 7)).charges().stream()
                .filter(c -> c.kind() == ChargeKind.ENROLLMENT_FEE)).extracting(PlannedCharge::slot).containsExactly(2);
    }

    @Test
    void installmentsAreNotChargedBeforeTheStudentsEntryMonth() {
        ChargePlan plan = plan(Builder.base(template(1, 2026, 10, 1, 2, null)).entry(ym(2026, 8)).fees(ym(2026, 2)), ym(2026, 7));

        assertThat(plan.charges()).isEmpty();
        assertThat(plan.decisions()).extracting(PlanDecision::code).contains("BEFORE_ENTRY");
    }

    @Test
    void windowDrivenEmissionHappensInTheMonthBeforeTheWindowOpensAndItIsConfigurable() {
        ProgramYearBillingTemplate t = template(1, 2027, 10, 1, 2, null);
        Builder builder = Builder.base(t).window(1, 1, LocalDate.of(2027, 1, 25), LocalDate.of(2027, 3, 10));

        assertThat(plan(builder, ym(2026, 11)).charges()).isEmpty();
        ChargePlan december = planner.plan(builder.at(ym(2026, 12), LocalDate.of(2026, 12, 2)), ChargeScope.ALL);
        assertThat(december.charges()).hasSize(1);
        assertThat(december.charges().get(0).dueDate()).isEqualTo(LocalDate.of(2027, 3, 10));
        assertThat(december.charges().get(0).priceDate()).isEqualTo(LocalDate.of(2026, 12, 2));
        assertThat(december.charges().get(0).period()).isEqualTo(ym(2027, 2));

        t.setEnrollmentFeeLeadMonths(0);
        assertThat(plan(builder, ym(2026, 12)).charges()).isEmpty();
        assertThat(planner.plan(builder.at(ym(2027, 1), LocalDate.of(2027, 1, 2)), ChargeScope.ALL).charges()).hasSize(1);
    }

    @Test
    void aFeeEmittedAfterTheWindowClosedIsDueTenDaysLaterAndNeverBornOverdueAndTheDaysAreConfigurable() {
        ProgramYearBillingTemplate t = template(1, 2027, 10, 1, 2, null);
        Builder builder = Builder.base(t).window(1, 1, LocalDate.of(2027, 1, 25), LocalDate.of(2027, 3, 10));

        ChargePlan april = planner.plan(builder.at(ym(2027, 4), LocalDate.of(2027, 4, 2)), ChargeScope.ENROLLMENT_FEE_ONLY);
        assertThat(april.charges()).hasSize(1);
        assertThat(april.charges().get(0).dueDate()).isEqualTo(LocalDate.of(2027, 4, 12));

        t.setLateEnrollmentFeeDueDays(30);
        assertThat(planner.plan(builder.at(ym(2027, 4), LocalDate.of(2027, 4, 2)), ChargeScope.ENROLLMENT_FEE_ONLY).charges().get(0).dueDate())
                .isEqualTo(LocalDate.of(2027, 5, 2));
    }

    @Test
    void pausedEnrollmentsAreNotBilledUnlessTheYearIsConfiguredToChargeWhilePaused() {
        ProgramYearBillingTemplate t = template(1, 2026, 10, 1, 3, null);
        Builder paused = Builder.base(t).isPaused();

        assertThat(plan(paused, ym(2026, 3)).charges()).isEmpty();
        assertThat(plan(paused, ym(2026, 3)).decisions()).extracting(PlanDecision::code).contains("PAUSED_NOT_BILLED");

        t.setPaysWhilePaused(true);
        assertThat(plan(paused, ym(2026, 3)).charges()).hasSize(2);
    }

    @Test
    void aCancelledFeeIsNeverIssuedAgainAndIsReportedWithItsOwnCode() {
        ProgramYearBillingTemplate t = template(1, 2026, 10, 1, 3, null);
        YearCycles cycles = new YearCycles(List.of(YearCycle.of(t, 2026)), null);
        ChargePlanningFacts facts = new ChargePlanningFacts(1L, ym(2026, 3), LocalDate.of(2026, 3, 1), true, false, false, false, false, null,
                PaymentPlanCadence.MONTHLY, true, cycles, List.of(new IssuedEnrollmentFee(9L, 1L, 1, ym(2026, 3), InvoiceStatus.CANCELLED, LocalDate.of(2026, 3, 1))),
                Set.of(), Map.of());

        ChargePlan plan = planner.plan(facts, ChargeScope.ENROLLMENT_FEE_ONLY);

        assertThat(plan.charges()).isEmpty();
        assertThat(plan.decisions()).extracting(PlanDecision::code).contains("FEE_CANCELLED");
    }

    @Test
    void movingTheSecondFeeMonthOfARunningYearDoesNotCreateAnotherFeeBecauseSlotsAreTrackedByNumber() {
        ProgramYearBillingTemplate t = template(1, 2026, 10, 2, 2, 7);
        YearCycles cycles = new YearCycles(List.of(YearCycle.of(t, 2026)), null);
        List<IssuedEnrollmentFee> issued = List.of(
                new IssuedEnrollmentFee(1L, 1L, 1, ym(2026, 2), InvoiceStatus.PAID, LocalDate.of(2026, 2, 1)),
                new IssuedEnrollmentFee(2L, 1L, 2, ym(2026, 7), InvoiceStatus.PENDING, LocalDate.of(2026, 7, 1)));
        t.configureEnrollmentFees(2, 2, 8);
        ChargePlanningFacts facts = new ChargePlanningFacts(1L, ym(2026, 9), LocalDate.of(2026, 9, 1), true, false, false, false, false, null,
                PaymentPlanCadence.MONTHLY, true, cycles, issued, Set.of(), Map.of());

        assertThat(planner.plan(facts, ChargeScope.ENROLLMENT_FEE_ONLY).charges()).isEmpty();
    }

    @Test
    void firstFeeMonthsFarAfterTheTermStartBelongToThePreviousCalendarYear() {
        YearCycle december = YearCycle.of(template(1, 2027, 10, 1, 12, null), 2027);
        YearCycle february = YearCycle.of(template(2, 2027, 10, 1, 2, null), 2027);

        assertThat(december.slotPeriod(1)).isEqualTo(ym(2026, 12));
        assertThat(december.start()).isEqualTo(ym(2026, 12));
        assertThat(february.slotPeriod(1)).isEqualTo(ym(2027, 2));
    }

    @Test
    void anUndatedTermUsesItsOwnYearSoTheCapOfASecondYearStartsFromZero() {
        Term term = new Term();
        term.setYear(2027);
        ProgramYearBillingTemplate t = new ProgramYearBillingTemplate();
        t.setId(2L);
        t.setTerm(term);
        t.setRecurringFeeInvoicesCount(2);
        YearCycle cycle = YearCycle.of(t, 2026);
        Set<YearMonth> issuedLastYear = Set.of(ym(2026, 3), ym(2026, 4), ym(2026, 5));

        assertThat(issuedLastYear.stream().filter(cycle::installmentRangeContains)).isEmpty();
        assertThat(cycle.installmentRangeContains(ym(2027, 3))).isTrue();
    }

    @Test
    void yearGapsAreDetectedOnlyWhenACalendarYearHasNoCycleBetweenTwoConfiguredYears() {
        YearCycles cycles = new YearCycles(List.of(YearCycle.of(template(1, 2026, 10, 1, 2, null), 2026),
                YearCycle.of(template(2, 2028, 10, 1, 2, null), 2028)), null);

        assertThat(cycles.hasGapAt(ym(2027, 5))).isTrue();
        assertThat(cycles.hasGapAt(ym(2026, 5))).isFalse();
        assertThat(cycles.hasGapAt(ym(2028, 5))).isFalse();
        assertThat(cycles.hasGapAt(ym(2029, 5))).isFalse();
    }

    @Test
    void aWindowThatAlreadyOpenedAndHasNoFeeIsEmittedImmediately() {
        ProgramYearBillingTemplate t = template(1, 2027, 10, 1, 2, null);
        Builder builder = Builder.base(t).window(1, 1, LocalDate.of(2027, 1, 25), LocalDate.of(2027, 3, 10));

        ChargePlan plan = planner.plan(builder.at(ym(2027, 2), LocalDate.of(2027, 2, 14)), ChargeScope.ENROLLMENT_FEE_ONLY);

        assertThat(plan.charges()).hasSize(1);
    }

    @Test
    void notConfiguredIsOnlyRaisedBeyondTheLastConfiguredYearNotBetweenTwoYears() {
        ProgramYearBillingTemplate y26 = template(1, 2026, 10, 1, 2, null);
        ProgramYearBillingTemplate y27 = template(2, 2027, 10, 1, 2, null);

        assertThat(plan(Builder.base(y26, y27), ym(2027, 1)).notConfigured()).isFalse();
        assertThat(plan(Builder.base(y26), ym(2027, 1)).notConfigured()).isTrue();
        assertThat(plan(Builder.base(y26), ym(2027, 1)).charges()).isEmpty();
        assertThat(plan(Builder.base(y26), ym(2027, 1)).decisions()).extracting(PlanDecision::code).contains("YEAR_NOT_CONFIGURED");
    }

    @Test
    void configurationRejectsInconsistentEnrollmentFeeSetups() {
        ProgramYearBillingTemplate t = template(1, 2026, 10, 1, 2, null);

        assertThatThrownBy(() -> t.configureEnrollmentFees(3, 2, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> t.configureEnrollmentFees(2, 7, 2)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> t.configureEnrollmentFees(2, 2, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> t.configureEnrollmentFees(1, 13, null)).isInstanceOf(RuntimeException.class);
        t.configureEnrollmentFees(1, 4, 9);
        assertThat(t.getSecondEnrollmentFeeMonth()).isNull();
    }

    @Test
    void slotRangesPartitionTheCycleAndTheFirstSlotCoversTheEntireYearWithOneFee() {
        YearCycle one = YearCycle.of(template(1, 2026, 10, 1, 2, null), 2026);
        YearCycle two = YearCycle.of(template(2, 2026, 10, 2, 2, 7), 2026);

        assertThat(one.slotContains(1, ym(2026, 12))).isTrue();
        assertThat(two.slotContains(1, ym(2026, 6))).isTrue();
        assertThat(two.slotContains(1, ym(2026, 7))).isFalse();
        assertThat(two.slotContains(2, ym(2026, 7))).isTrue();
        assertThat(two.slotOf(ym(2026, 8))).isEqualTo(2);
        assertThat(one.slotOf(ym(2026, 8))).isEqualTo(1);
    }
}
