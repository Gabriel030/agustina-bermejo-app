package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-136")
@Tag("D-139")
class OldestOverdueFirstAllocationStrategyProperties {

    private final OldestOverdueFirstAllocationStrategy strategy = new OldestOverdueFirstAllocationStrategy();

    @Provide
    Arbitrary<List<OpenInvoiceView>> openInvoices() {
        Arbitrary<Long> cents = Arbitraries.longs().between(1, 20_000_000);
        Arbitrary<Integer> dueOffsets = Arbitraries.integers().between(0, 120);
        Arbitrary<InvoiceDocumentType> types = Arbitraries.of(InvoiceDocumentType.values());
        return Combinators.combine(cents, dueOffsets, types).as((amount, offset, type) -> new Object[]{amount, offset, type})
                .list().ofMaxSize(12)
                .map(rows -> IntStream.range(0, rows.size()).mapToObj(index -> {
                    Object[] row = rows.get(index);
                    return new OpenInvoiceView((long) index + 1, 1L, (InvoiceDocumentType) row[2], null,
                            LocalDate.of(2027, 3, 1).plusDays((Integer) row[1]), LocalDate.of(2027, 2, 1),
                            NonNegativeMoney.of(BigDecimal.valueOf((Long) row[0], 2), Currency.ARS));
                }).toList());
    }

    @Provide
    Arbitrary<NonNegativeMoney> credits() {
        return Arbitraries.longs().between(0, 100_000_000).map(cents -> NonNegativeMoney.of(BigDecimal.valueOf(cents, 2), Currency.ARS));
    }

    @Property
    void theTotalPlannedNeverExceedsTheCreditAndEveryItemIsTheWholeOutstanding(@ForAll("openInvoices") List<OpenInvoiceView> open,
                                                                              @ForAll("credits") NonNegativeMoney credit) {
        List<PlannedInvoiceAllocation> plan = strategy.planFullInvoiceAllocations(credit, open);

        Money total = plan.stream().map(planned -> planned.amount().value()).reduce(Money.zero(Currency.ARS), Money::plus);
        assertThat(credit.value().isGreaterThan(total) || credit.value().equals(total)).isTrue();
        plan.forEach(planned -> assertThat(planned.amount()).isEqualTo(open.stream()
                .filter(invoice -> invoice.invoiceId().equals(planned.invoiceId())).findFirst().orElseThrow().outstandingAmount()));
    }

    @Property
    void noUnallocatedInvoiceIsOlderThanAnAllocatedOne(@ForAll("openInvoices") List<OpenInvoiceView> open, @ForAll("credits") NonNegativeMoney credit) {
        List<Long> plannedIds = strategy.planFullInvoiceAllocations(credit, open).stream().map(PlannedInvoiceAllocation::invoiceId).toList();
        List<OpenInvoiceView> ordered = strategy.sortInAllocationOrder(open);

        assertThat(ordered.stream().limit(plannedIds.size()).map(OpenInvoiceView::invoiceId).toList()).isEqualTo(plannedIds);
    }

    @Property
    void theNextInvoiceAfterThePlanDoesNotFitInTheRemainingCredit(@ForAll("openInvoices") List<OpenInvoiceView> open, @ForAll("credits") NonNegativeMoney credit) {
        List<PlannedInvoiceAllocation> plan = strategy.planFullInvoiceAllocations(credit, open);
        List<OpenInvoiceView> ordered = strategy.sortInAllocationOrder(open);
        Money remaining = plan.stream().map(planned -> planned.amount().value()).reduce(credit.value(), Money::minus);

        if (plan.size() < ordered.size()) {
            assertThat(ordered.get(plan.size()).outstandingAmount().value().isGreaterThan(remaining)).isTrue();
        }
    }
}
