package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.repository.LiveMonthlyInstallmentPeriodRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-238")
class NextMonthlyInstallmentPeriodQueryTest {
    private final LiveMonthlyInstallmentPeriodRepository repository = mock(LiveMonthlyInstallmentPeriodRepository.class);
    private final NextMonthlyInstallmentPeriodQuery query = new NextMonthlyInstallmentPeriodQuery(repository);
    private final EnrollmentReference program = EnrollmentReference.ofProgram(7L);

    @Test
    void withNothingEmittedTheNextInstallmentIsTheCurrentMonth() {
        when(repository.findLivePeriodsOfProgramEnrollment(7L)).thenReturn(List.of());

        assertThat(query.findNextMonthlyInstallmentPeriod(program, LocalDate.of(2027, 3, 31))).isEqualTo(YearMonth.of(2027, 3));
    }

    @Test
    void anEmittedCurrentMonthMovesTheNextInstallmentToTheFollowingOne() {
        when(repository.findLivePeriodsOfProgramEnrollment(7L)).thenReturn(List.of(YearMonth.of(2027, 3)));

        assertThat(query.findNextMonthlyInstallmentPeriod(program, LocalDate.of(2027, 3, 10))).isEqualTo(YearMonth.of(2027, 4));
    }

    @Test
    void aMissingMonthBeforeAnEmittedOneIsStillTheNextInstallment() {
        when(repository.findLivePeriodsOfProgramEnrollment(7L)).thenReturn(List.of(YearMonth.of(2027, 4), YearMonth.of(2027, 5)));

        assertThat(query.findNextMonthlyInstallmentPeriod(program, LocalDate.of(2027, 3, 10))).isEqualTo(YearMonth.of(2027, 3));
    }

    @Test
    void consecutiveEmittedMonthsAcrossAYearEndAreSkipped() {
        when(repository.findLivePeriodsOfCourseEnrollment(9L)).thenReturn(List.of(YearMonth.of(2027, 12), YearMonth.of(2028, 1)));

        assertThat(query.findNextMonthlyInstallmentPeriod(EnrollmentReference.ofCourse(9L), LocalDate.of(2027, 12, 20))).isEqualTo(YearMonth.of(2028, 2));
    }

    @Test
    void emittedMonthsBeforeTodayDoNotMatter() {
        when(repository.findLivePeriodsOfProgramEnrollment(7L)).thenReturn(List.of(YearMonth.of(2027, 1), YearMonth.of(2027, 2)));

        assertThat(query.findNextMonthlyInstallmentPeriod(program, LocalDate.of(2027, 3, 1))).isEqualTo(YearMonth.of(2027, 3));
    }
}
