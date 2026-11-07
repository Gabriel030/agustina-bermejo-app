package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotDueThisMonthSpecification;

import java.time.LocalDate;
import java.util.Optional;

public record AdvanceEnrollmentFeeCalendar(LocalDate windowOpensOn, LocalDate windowClosesOn, LocalDate advanceOpensOn, LocalDate regularEmissionStart) {
    private static final int FIRST_SLOT = 1;

    public static Optional<AdvanceEnrollmentFeeCalendar> of(Optional<SlotWindow> window, YearCycle cycle) {
        return window.map(slotWindow -> {
            LocalDate regularEmissionStart = EnrollmentFeeSlotDueThisMonthSpecification.emissionMonth(slotWindow, cycle, FIRST_SLOT).atDay(1);
            LocalDate configured = cycle.yearTerms().advanceEnrollmentFeeOpensOn();
            return new AdvanceEnrollmentFeeCalendar(slotWindow.opensOn(), slotWindow.closesOn(), configured != null ? configured : slotWindow.opensOn(), regularEmissionStart);
        });
    }

    public boolean openingAfterRegularEmission() {
        return advanceOpensOn.isAfter(regularEmissionStart);
    }

    public boolean openingAfterWindowClose() {
        return advanceOpensOn.isAfter(windowClosesOn);
    }
}
