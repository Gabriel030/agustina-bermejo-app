package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.HolidaysNotLoadedForYearException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class HolidayYearGuard {

    private final HolidayRepository holidayRepository;
    private final Clock clock;

    public HolidayYearGuard(HolidayRepository holidayRepository, Clock clock) {
        this.holidayRepository = holidayRepository;
        this.clock = clock;
    }

    public void ensureHolidaysLoaded(int year) {
        if (year < LocalDate.now(clock).getYear()) {
            return;
        }
        boolean loaded = holidayRepository.existsByDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        if (!loaded) {
            throw new HolidaysNotLoadedForYearException(year);
        }
    }
}
