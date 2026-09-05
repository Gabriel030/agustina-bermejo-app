package com.red_escuela.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentScheduleCalculatorTest {

    @Test
    void beforeOfTheEnrollmentNotCorrespondsNoInstallment() {
        LocalDate courseStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(courseStart, 10, LocalDate.of(2026, 2, 15)))
                .isNull();
    }

    @Test
    void theMonthOfStartIsTheInstallment1() {
        LocalDate courseStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(courseStart, 10, LocalDate.of(2026, 3, 15)))
                .isEqualTo(1);
    }

    @Test
    void aPlanOf10InstallmentsFromMarchWalksCorrectlyUntilDecember() {
        LocalDate courseStart = LocalDate.of(2026, 3, 1);
        int[] expected = IntStream.rangeClosed(1, 10).toArray();
        for (int month = 0; month < 10; month++) {
            LocalDate date = courseStart.plusMonths(month);
            assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(courseStart, 10, date))
                    .as("mes " + month)
                    .isEqualTo(expected[month]);
        }
    }

    @Test
    void aTimeExhaustedThePlanNotReturnsMoreInstallments() {
        LocalDate courseStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(courseStart, 10, LocalDate.of(2027, 1, 1)))
                .isNull();
    }

    @Test
    void aCourseOfThreeInstallmentsGivesNullInTheFourthInstallment() {
        LocalDate courseStart = LocalDate.of(2026, 5, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(courseStart, 3, LocalDate.of(2026, 7, 20)))
                .isEqualTo(3);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(courseStart, 3, LocalDate.of(2026, 8, 1)))
                .isNull();
    }
}
