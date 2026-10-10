package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
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

    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2028, 1, 1);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");
    private final OldestOverdueFirstAllocationStrategy strategy = new OldestOverdueFirstAllocationStrategy();

    private static NonNegativeMoney ars(long cents) {
        return NonNegativeMoney.of(BigDecimal.valueOf(cents, 2), Currency.ARS);
    }

    private static long cents(NonNegativeMoney money) {
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

    private List<OpenInvoiceView> openInvoices(List<Long> outstandingCents) {
        List<OpenInvoiceView> views = new ArrayList<>();
        for (int index = 0; index < outstandingCents.size(); index++) {
            views.add(new OpenInvoiceView((long) index + 1, 1L, InvoiceDocumentType.INVOICE, null,
                    START.plusDays(index), START.minusDays(index), ars(outstandingCents.get(index))));
        }
        return views;
    }

    private CreditContributions contributionsTotalling(long totalCents) {
        long half = totalCents / 2;
        return new CreditContributions(List.of(new CreditContribution(START, ars(half)), new CreditContribution(START.plusDays(10), ars(totalCents - half))));
    }

    @Property(tries = 1000)
    void theScheduledAmountsNeverExceedTheCreditNorTheOutstandingOfEachInvoice(@ForAll("centAmounts") List<Long> outstanding, @ForAll("credit") long creditCents) {
        List<OpenInvoiceView> open = openInvoices(outstanding);
        NonNegativeMoney credit = ars(creditCents);
        List<PlannedInvoiceAllocation> plan = strategy.planFullInvoiceAllocations(credit, strategy.sortInAllocationOrder(open));

        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(plan, contributionsTotalling(creditCents), credit, BUSINESS_DATE);

        Money total = schedule.stream().map(scheduled -> scheduled.amount().value()).reduce(Money.zero(Currency.ARS), Money::plus);
        assertThat(credit.value().isGreaterThan(total) || credit.value().equals(total)).isTrue();
        assertThat(schedule).hasSameSizeAs(plan);
        for (ScheduledAllocation scheduled : schedule) {
            NonNegativeMoney invoiceOutstanding = open.stream().filter(view -> view.invoiceId().equals(scheduled.invoiceId())).findFirst().orElseThrow().outstandingAmount();
            assertThat(scheduled.amount()).isEqualTo(invoiceOutstanding);
        }
    }

    @Property(tries = 1000)
    void effectiveDatesNeverGoBackwardsNorPastTheBusinessDate(@ForAll("centAmounts") List<Long> amounts, @ForAll("credit") long creditCents) {
        List<PlannedInvoiceAllocation> plan = new ArrayList<>();
        for (int index = 0; index < amounts.size(); index++) {
            plan.add(new PlannedInvoiceAllocation((long) index + 1, ars(amounts.get(index))));
        }

        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(plan, contributionsTotalling(creditCents), ars(creditCents), BUSINESS_DATE);

        LocalDate previous = START;
        for (ScheduledAllocation scheduled : schedule) {
            assertThat(scheduled.effectiveDate()).isAfterOrEqualTo(previous).isBeforeOrEqualTo(BUSINESS_DATE);
            previous = scheduled.effectiveDate();
        }
    }

    @Property(tries = 1000)
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
