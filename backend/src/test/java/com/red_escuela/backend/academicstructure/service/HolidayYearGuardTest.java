package com.red_escuela.backend.academicstructure.service;

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
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayYearGuardTest {

    @Mock
    private HolidayRepository holidayRepository;

    private HolidayYearGuard guard;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
        guard = new HolidayYearGuard(holidayRepository, clock);
    }

    @Test
    void nextYearWithoutHolidaysIsRejected() {
        when(holidayRepository.existsByDateBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))).thenReturn(false);

        assertThatThrownBy(() -> guard.ensureHolidaysLoaded(2027)).isInstanceOf(HolidaysNotLoadedForYearException.class);
    }

    @Test
    void nextYearWithHolidaysIsAccepted() {
        when(holidayRepository.existsByDateBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))).thenReturn(true);

        assertThatCode(() -> guard.ensureHolidaysLoaded(2027)).doesNotThrowAnyException();
    }

    @Test
    void pastYearsAreNotChecked() {
        assertThatCode(() -> guard.ensureHolidaysLoaded(2025)).doesNotThrowAnyException();

        verifyNoInteractions(holidayRepository);
    }
}
