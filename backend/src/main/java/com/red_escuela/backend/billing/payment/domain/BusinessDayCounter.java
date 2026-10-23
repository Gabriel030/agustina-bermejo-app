package com.red_escuela.backend.billing.payment.domain;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class BusinessDayCounter {
    private final Predicate<LocalDate> isBusinessDay;

    public BusinessDayCounter(Predicate<LocalDate> isBusinessDay) {
        this.isBusinessDay = Objects.requireNonNull(isBusinessDay, "isBusinessDay");
    }

    public static BusinessDayCounter remembering(Predicate<LocalDate> isBusinessDay) {
        Map<LocalDate, Boolean> answers = new HashMap<>();
        return new BusinessDayCounter(day -> answers.computeIfAbsent(day, isBusinessDay::test));
    }

    public int countBusinessDaysAfter(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        int count = 0;
        for (LocalDate day = startDate.plusDays(1); !day.isAfter(endDate); day = day.plusDays(1)) {
            if (isBusinessDay.test(day)) {
                count++;
            }
        }
        return count;
    }

    public boolean hasReachedBusinessDaysAfter(LocalDate startDate, LocalDate endDate, int threshold) {
        return countBusinessDaysAfter(startDate, endDate) >= threshold;
    }
}
