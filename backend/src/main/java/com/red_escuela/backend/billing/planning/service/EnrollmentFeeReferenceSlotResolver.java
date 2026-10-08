package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotDueThisMonthSpecification;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;

public final class EnrollmentFeeReferenceSlotResolver {

    public Optional<EnrollmentFeeSlotReference> resolveReferenceSlotForPeriod(ProgramInvoicePlanningFacts facts, YearMonth reference) {
        return facts.cycles().covering(reference).map(cycle -> new EnrollmentFeeSlotReference(cycle, cycle.slotOf(reference)));
    }

    public Optional<EnrollmentFeeSlotReference> resolveLatestDueSlot(ProgramInvoicePlanningFacts facts) {
        return facts.cycles().all().stream()
                .flatMap(cycle -> IntStream.rangeClosed(1, cycle.slotCount()).mapToObj(slot -> new EnrollmentFeeSlotReference(cycle, slot)))
                .filter(reference -> EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts, reference.cycle(), reference.slot(), true))
                .reduce((current, candidate) -> isLaterThan(candidate, current) ? candidate : current);
    }

    private boolean isLaterThan(EnrollmentFeeSlotReference candidate, EnrollmentFeeSlotReference current) {
        return Comparator.comparing((EnrollmentFeeSlotReference reference) -> reference.cycle().start())
                .thenComparingInt(EnrollmentFeeSlotReference::slot).compare(candidate, current) > 0;
    }
}
