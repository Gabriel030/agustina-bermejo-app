package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-139")
class AllocationDomainProperties {

    static final LocalDate START = LocalDate.of(2027, 1, 1);
    static final LocalDate BUSINESS_DATE = LocalDate.of(2028, 1, 1);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");

    static NonNegativeMoney ars(long cents) {
        return NonNegativeMoney.of(BigDecimal.valueOf(cents, 2), Currency.ARS);
    }

    static long cents(NonNegativeMoney money) {
        return money.value().amount().movePointRight(2).longValueExact();
    }

    @Provide
    Arbitrary<List<Long>> centAmounts() {
        return Arbitraries.longs().between(1, 1_000_000).list().ofMinSize(0).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Long> credit() {
        return Arbitraries.longs().between(0, 5_000_000);
    }

    static List<CreditContribution> creditsOf(List<Long> creditCents) {
        List<CreditContribution> credits = new ArrayList<>();
        for (int index = 0; index < creditCents.size(); index++) {
            credits.add(new CreditContribution(1000L + index, 2000L + index, null, START.plusDays(index), ars(creditCents.get(index))));
        }
        return credits;
    }

    static long totalOf(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum();
    }

    static List<Long> fitInto(List<Long> amounts, long budget) {
        List<Long> fitted = new ArrayList<>();
        long remaining = budget;
        for (long amount : amounts) {
            long taken = Math.min(amount, remaining);
            if (taken <= 0) {
                break;
            }
            fitted.add(taken);
            remaining -= taken;
        }
        return fitted;
    }

    static List<PlannedInvoiceAllocation> planOf(List<Long> amounts) {
        List<PlannedInvoiceAllocation> plan = new ArrayList<>();
        for (int index = 0; index < amounts.size(); index++) {
            plan.add(new PlannedInvoiceAllocation(100L + index * 7L, ars(amounts.get(index))));
        }
        return plan;
    }

    @Property(tries = 1000, seed = "20261015")
    void theScheduleKeepsTheOrderIdsAndAmountsOfThePlan(@ForAll("centAmounts") List<Long> amounts, @ForAll("centAmounts") List<Long> creditCents,
                                                        @ForAll("credit") long requestedCents) {
        long toAllocateCents = Math.min(requestedCents, totalOf(creditCents));
        List<PlannedInvoiceAllocation> plan = planOf(fitInto(amounts, toAllocateCents));

        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(plan, new CreditContributions(creditsOf(creditCents)), BUSINESS_DATE);

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId).containsExactlyElementsOf(plan.stream().map(PlannedInvoiceAllocation::invoiceId).toList());
        assertThat(schedule).extracting(ScheduledAllocation::amount).containsExactlyElementsOf(plan.stream().map(PlannedInvoiceAllocation::amount).toList());
    }

    @Property(tries = 1000, seed = "20261015")
    void eachEffectiveDateIsTheCreditThatCoversThePlanSoFar(@ForAll("centAmounts") List<Long> amounts,
                                                                           @ForAll("centAmounts") List<Long> creditCents,
                                                                           @ForAll("credit") long requestedCents) {
        long total = totalOf(creditCents);
        long toAllocateCents = Math.min(requestedCents, total);
        List<Long> fitted = fitInto(amounts, toAllocateCents);

        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(planOf(fitted), new CreditContributions(creditsOf(creditCents)), BUSINESS_DATE);

        long target = 0;
        LocalDate previous = START;
        for (int item = 0; item < schedule.size(); item++) {
            target += fitted.get(item);
            LocalDate expected = BUSINESS_DATE;
            long cumulative = 0;
            for (int credit = 0; credit < creditCents.size(); credit++) {
                cumulative += creditCents.get(credit);
                if (target <= cumulative) {
                    expected = START.plusDays(credit);
                    break;
                }
            }
            assertThat(schedule.get(item).effectiveDate()).isEqualTo(expected).isAfterOrEqualTo(previous);
            previous = schedule.get(item).effectiveDate();
        }
    }

    @Property(tries = 1000, seed = "20261015")
    void theSelectionIsAMinimalPrefixThatCoversWhatMustBeRemoved(@ForAll("centAmounts") List<Long> amounts, @ForAll("credit") long unallocatedCents,
                                                                  @ForAll("credit") long toRemoveCents) {
        List<PaymentAllocation> newestFirst = new ArrayList<>();
        for (int index = 0; index < amounts.size(); index++) {
            long cents = amounts.get(index);
            newestFirst.add(PaymentAllocation.recordFullInvoiceAllocation(AccountTestData.issuedInvoice(index + 1L, BigDecimal.valueOf(cents, 2).toPlainString()),
                    ars(cents), BUSINESS_DATE, BUSINESS_DATE, ACTOR));
        }

        List<PaymentAllocation> selected = NewestAllocationsReversalSelection.selectAllocationsToReverse(newestFirst, ars(unallocatedCents), ars(toRemoveCents));

        assertThat(selected).containsExactlyElementsOf(newestFirst.subList(0, selected.size()));
        long restored = unallocatedCents + selected.stream().mapToLong(allocation -> cents(allocation.getAmount())).sum();
        if (selected.size() < newestFirst.size()) {
            assertThat(restored).isGreaterThanOrEqualTo(toRemoveCents);
        }
        if (!selected.isEmpty()) {
            long withoutLast = restored - cents(selected.get(selected.size() - 1).getAmount());
            assertThat(withoutLast).isLessThan(toRemoveCents);
        }
    }
}
