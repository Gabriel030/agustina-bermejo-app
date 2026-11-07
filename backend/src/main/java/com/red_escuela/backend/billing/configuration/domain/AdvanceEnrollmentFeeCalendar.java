package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

public record AdvanceEnrollmentFeeCalendar(LocalDate windowOpensOn, LocalDate windowClosesOn, LocalDate advanceOpensOn, LocalDate regularEmissionStart) {

    public static Optional<AdvanceEnrollmentFeeCalendar> of(LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd, LocalDateTime termStart, YearBillingTerms terms) {
        if (enrollmentStart == null || (enrollmentEnd == null && termStart == null)) {
            return Optional.empty();
        }
        LocalDate opens = enrollmentStart.toLocalDate();
        LocalDate closes = (enrollmentEnd != null ? enrollmentEnd : termStart).toLocalDate();
        LocalDate regularEmissionStart = YearMonth.from(opens).minusMonths(terms.enrollmentFeeLeadMonths()).atDay(1);
        LocalDate advanceOpensOn = terms.advanceEnrollmentFeeOpensOn() != null ? terms.advanceEnrollmentFeeOpensOn() : opens;
        return Optional.of(new AdvanceEnrollmentFeeCalendar(opens, closes, advanceOpensOn, regularEmissionStart));
    }

    public boolean openingAfterRegularEmission() {
        return advanceOpensOn.isAfter(regularEmissionStart);
    }

    public boolean openingAfterWindowClose() {
        return advanceOpensOn.isAfter(windowClosesOn);
    }
}
