package com.red_escuela.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentScheduleCalculatorTest {

    @Test
    void beforeOfTheEnrollmentNotCorrespondsNoInstallment() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(cursadaStart, 10, LocalDate.of(2026, 2, 15)))
                .isNull();
    }

    @Test
    void theMonthOfStartIsTheInstallment1() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(cursadaStart, 10, LocalDate.of(2026, 3, 15)))
                .isEqualTo(1);
    }

    @Test
    void aPlanOf10InstallmentsFromMarchWalksCorrectlyUntilDecember() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        int[] esperado = IntStream.rangeClosed(1, 10).toArray();
        for (int mes = 0; mes < 10; mes++) {
            LocalDate fecha = cursadaStart.plusMonths(mes);
            assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(cursadaStart, 10, fecha))
                    .as("mes " + mes)
                    .isEqualTo(esperado[mes]);
        }
    }

    @Test
    void aTimeExhaustedThePlanNotReturnsMoreInstallments() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(cursadaStart, 10, LocalDate.of(2027, 1, 1)))
                .isNull();
    }

    @Test
    void aCourseOfThreeInstallmentsGivesNullInTheFourthInstallment() {
        LocalDate cursadaStart = LocalDate.of(2026, 5, 1);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(cursadaStart, 3, LocalDate.of(2026, 7, 20)))
                .isEqualTo(3);
        assertThat(InstallmentScheduleCalculator.calculateInstallmentNumber(cursadaStart, 3, LocalDate.of(2026, 8, 1)))
                .isNull();
    }
}
