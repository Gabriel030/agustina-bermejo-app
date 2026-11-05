package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AllocationSchedule {

    private AllocationSchedule() {
    }

    public static List<ScheduledAllocation> scheduleInvoiceAllocations(List<PlannedInvoiceAllocation> plan, CreditContributions availableCredit,
                                                                      LocalDate businessDate) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(availableCredit, "availableCredit");
        Objects.requireNonNull(businessDate, "businessDate");
        List<CreditContribution> contributions = availableCredit.asList();
        List<Money> left = new ArrayList<>(contributions.stream().map(contribution -> contribution.amount().value()).toList());
        List<ScheduledAllocation> schedule = new ArrayList<>();
        for (PlannedInvoiceAllocation planned : plan) {
            Money pending = planned.amount().value();
            LocalDate effectiveDate = businessDate;
            List<AllocationSourceShare> shares = new ArrayList<>();
            for (int index = 0; index < contributions.size() && !pending.isZero(); index++) {
                Money taken = pending.isGreaterThan(left.get(index)) ? left.get(index) : pending;
                if (taken.isZero()) {
                    continue;
                }
                CreditContribution contribution = contributions.get(index);
                shares.add(new AllocationSourceShare(contribution.entryId(), contribution.paymentId(), contribution.creditNoteId(), NonNegativeMoney.of(taken)));
                left.set(index, left.get(index).minus(taken));
                pending = pending.minus(taken);
                effectiveDate = contribution.effectiveDate();
            }
            schedule.add(new ScheduledAllocation(planned.invoiceId(), planned.amount(), effectiveDate, shares));
        }
        return List.copyOf(schedule);
    }
}
