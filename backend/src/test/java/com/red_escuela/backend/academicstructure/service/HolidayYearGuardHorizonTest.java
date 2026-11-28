package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.HolidaysNotLoadedForYearException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayYearGuardHorizonTest {

    @Mock
    private HolidayRepository holidayRepository;

    private HolidayYearGuard guard;

    @BeforeEach
    void setUp() {
        guard = new HolidayYearGuard(holidayRepository, Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void currentYearPlusTwoIsStillCheckedForATerm() {
        when(holidayRepository.countByDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).thenReturn(5L);
        when(holidayRepository.countByDateBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))).thenReturn(5L);
        when(holidayRepository.countByDateBetween(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31))).thenReturn(0L);
        Term term = new Term();
        term.setStartDate(LocalDateTime.of(2026, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2030, 6, 30, 23, 59));

        assertThatThrownBy(() -> guard.ensureHolidaysLoadedForTerm(term)).isInstanceOf(HolidaysNotLoadedForYearException.class);
    }

    @Test
    void aTermStartingBeyondTheHorizonStillChecksItsStartYear() {
        when(holidayRepository.countByDateBetween(LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31))).thenReturn(0L);
        Term term = new Term();
        term.setStartDate(LocalDateTime.of(2045, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2999, 6, 30, 23, 59));

        assertThatThrownBy(() -> guard.ensureHolidaysLoadedForTerm(term)).isInstanceOf(HolidaysNotLoadedForYearException.class);
        verify(holidayRepository, never()).countByDateBetween(LocalDate.of(2046, 1, 1), LocalDate.of(2046, 12, 31));
    }

    @Test
    void aTermEndingFarInTheFutureOnlyChecksUpToCurrentPlusTwo() {
        when(holidayRepository.countByDateBetween(any(), any())).thenReturn(5L);
        Term term = new Term();
        term.setStartDate(LocalDateTime.of(2026, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2999, 6, 30, 23, 59));

        assertThatCode(() -> guard.ensureHolidaysLoadedForTerm(term)).doesNotThrowAnyException();

        verify(holidayRepository, never()).countByDateBetween(LocalDate.of(2029, 1, 1), LocalDate.of(2029, 12, 31));
        verify(holidayRepository).countByDateBetween(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
    }
}
