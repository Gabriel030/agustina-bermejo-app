package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.InstallmentBusinessDays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInstallmentDueDateCalculatorBillingHolidayTest {

    private static MonthlyInstallmentDueDateCalculator calculatorWithBillingHolidays(LocalDate... holidays) {
        Set<LocalDate> billingHolidays = Set.of(holidays);
        InstallmentBusinessDays calendar = date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !billingHolidays.contains(date);
        return new MonthlyInstallmentDueDateCalculator(calendar);
    }

    @Test
    @Tag("D-010")
    void dueDateOnABillingHolidayMovesToTheNextBusinessDay() {
        var calculator = calculatorWithBillingHolidays(LocalDate.of(2027, 3, 24));

        assertThat(calculator.calculateInstallmentDueDate(LocalDate.of(2027, 3, 14), 10)).isEqualTo(LocalDate.of(2027, 3, 25));
    }

    @Test
    @Tag("D-010")
    void aHolidayOnTheMondayAfterTheWeekendMovesToTuesday() {
        var calculator = calculatorWithBillingHolidays(LocalDate.of(2027, 3, 15));

        assertThat(calculator.calculateInstallmentDueDate(LocalDate.of(2027, 3, 3), 10)).isEqualTo(LocalDate.of(2027, 3, 16));
    }

    @Test
    @Tag("D-010")
    void consecutiveHolidaysAreAllSkipped() {
        var calculator = calculatorWithBillingHolidays(LocalDate.of(2027, 3, 24), LocalDate.of(2027, 3, 25));

        assertThat(calculator.calculateInstallmentDueDate(LocalDate.of(2027, 3, 14), 10)).isEqualTo(LocalDate.of(2027, 3, 26));
    }

    @Test
    void aBusinessDayDueDateIsNotMoved() {
        var calculator = calculatorWithBillingHolidays(LocalDate.of(2027, 3, 24));

        assertThat(calculator.calculateInstallmentDueDate(LocalDate.of(2027, 3, 8), 10)).isEqualTo(LocalDate.of(2027, 3, 18));
    }
}
