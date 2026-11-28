package com.red_escuela.backend.billing.planning.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

@FunctionalInterface
public interface InstallmentBusinessDays {

    InstallmentBusinessDays WEEKDAYS_ONLY =
            date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;

    boolean isBusinessDay(LocalDate date);
}
