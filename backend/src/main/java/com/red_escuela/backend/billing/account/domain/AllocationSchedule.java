package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AllocationSchedule {

    private AllocationSchedule() {
    }

    public static List<ScheduledAllocation> scheduleInvoiceAllocations(List<PlannedInvoiceAllocation> plan, CreditContributions credits,
                                                                      NonNegativeMoney creditToAllocate, LocalDate businessDate) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(credits, "credits");
        Objects.requireNonNull(creditToAllocate, "creditToAllocate");
        NonNegativeMoney consumedCredit = credits.consumedCreditGiven(creditToAllocate);
        List<ScheduledAllocation> schedule = new ArrayList<>();
        for (PlannedInvoiceAllocation planned : plan) {
            LocalDate effectiveDate = AllocationEffectiveDateCalculator.calculateEffectiveDate(credits.asList(), consumedCredit, planned.amount(), businessDate);
            schedule.add(new ScheduledAllocation(planned.invoiceId(), planned.amount(), effectiveDate, credits.sharesCovering(consumedCredit, planned.amount())));
            consumedCredit = consumedCredit.plus(planned.amount());
        }
        return List.copyOf(schedule);
    }
}
