package com.red_escuela.backend.billing.account.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.BUSINESS_DATE;
import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.START;
import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.ars;
import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.cents;
import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.creditsOf;
import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.fitInto;
import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.planOf;
import static com.red_escuela.backend.billing.account.domain.AllocationDomainProperties.totalOf;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-376")
@Tag("D-139")
class AllocationSourcesProperties {

    @Provide
    Arbitrary<List<Long>> centAmounts() {
        return Arbitraries.longs().between(1, 1_000_000).list().ofMinSize(0).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Long> credit() {
        return Arbitraries.longs().between(0, 5_000_000);
    }

    @Property(tries = 1000, seed = "20261015")
    void theSourcesOfEachAllocationAddUpToItAndEndInTheCreditThatFixesItsEffectiveDate(@ForAll("centAmounts") List<Long> amounts,
                                                                                         @ForAll("centAmounts") List<Long> creditCents,
                                                                                         @ForAll("credit") long requestedCents) {
        long toAllocateCents = Math.min(requestedCents, totalOf(creditCents));
        List<Long> fitted = fitInto(amounts, toAllocateCents);

        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(planOf(fitted), new CreditContributions(creditsOf(creditCents)), BUSINESS_DATE);

        long[] takenFromEntry = new long[creditCents.size()];
        for (ScheduledAllocation allocation : schedule) {
            assertThat(allocation.sources().stream().mapToLong(share -> cents(share.amount())).sum()).isEqualTo(cents(allocation.amount()));
            AllocationSourceShare last = allocation.sources().get(allocation.sources().size() - 1);
            assertThat(START.plusDays(last.creditEntryId() - 1000L)).isEqualTo(allocation.effectiveDate());
            allocation.sources().forEach(share -> takenFromEntry[(int) (share.creditEntryId() - 1000L)] += cents(share.amount()));
        }
        for (int entry = 0; entry < takenFromEntry.length; entry++) {
            assertThat(takenFromEntry[entry]).isLessThanOrEqualTo(creditCents.get(entry));
        }
    }
}
