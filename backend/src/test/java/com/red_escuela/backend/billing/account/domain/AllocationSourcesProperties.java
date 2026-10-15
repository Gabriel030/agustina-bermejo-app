package com.red_escuela.backend.billing.account.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Tag;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Property(tries = 1000, seed = "20261015")
    void theCreditAvailableFromEntriesFeedsTheScheduleWithoutExceedingWhatEachEntryHasLeft(@ForAll("centAmounts") List<Long> paymentCents,
                                                                                            @ForAll("credit") long sourcedSeed, @ForAll("credit") long leftSeed,
                                                                                            @ForAll("centAmounts") List<Long> planCents) {
        var account = AccountTestData.arsAccount();
        List<CurrentAccountEntry> entries = new ArrayList<>();
        Map<Long, NonNegativeMoney> sourced = new HashMap<>();
        long[] room = new long[paymentCents.size()];
        long roomTotal = 0;
        for (int index = 0; index < paymentCents.size(); index++) {
            CurrentAccountEntry entry = AccountTestData.withEntryId(account.recordPaymentReceivedCredit(index + 1L, ars(paymentCents.get(index)), START.plusDays(index), "Pago"));
            entries.add(entry);
            long alreadySourced = (sourcedSeed + 7L * index) % (paymentCents.get(index) + 1);
            if (alreadySourced > 0) {
                sourced.put(entry.getId(), ars(alreadySourced));
            }
            room[index] = paymentCents.get(index) - alreadySourced;
            roomTotal += room[index];
        }
        long unallocated = roomTotal - leftSeed % (roomTotal + 1);

        CreditContributions available = CreditContributions.availableFrom(entries, sourced, ars(unallocated));
        List<Long> fitted = fitInto(planCents, unallocated);
        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(planOf(fitted), available, BUSINESS_DATE);

        assertThat(available.asList().stream().mapToLong(contribution -> cents(contribution.amount())).sum()).isEqualTo(unallocated);
        long[] takenFromEntry = new long[paymentCents.size()];
        int lastEntryIndex = -1;
        for (ScheduledAllocation allocation : schedule) {
            assertThat(allocation.sources().stream().mapToLong(share -> cents(share.amount())).sum()).isEqualTo(cents(allocation.amount()));
            for (AllocationSourceShare share : allocation.sources()) {
                int entryIndex = (int) (share.creditEntryId() - 1000L) - 1;
                assertThat(entryIndex).isGreaterThanOrEqualTo(lastEntryIndex);
                lastEntryIndex = entryIndex;
                takenFromEntry[entryIndex] += cents(share.amount());
            }
            AllocationSourceShare last = allocation.sources().get(allocation.sources().size() - 1);
            assertThat(START.plusDays(last.creditEntryId() - 1000L - 1)).isEqualTo(allocation.effectiveDate());
        }
        for (int entry = 0; entry < takenFromEntry.length; entry++) {
            assertThat(takenFromEntry[entry]).isLessThanOrEqualTo(room[entry]);
        }
    }
}
