package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.HolidaysNotLoadedForYearException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class HolidayYearGuard {

    public static final int MINIMUM_HOLIDAYS_PER_YEAR = 5;
    public static final int YEARS_AHEAD_REQUIRED = 2;

    private final HolidayRepository holidayRepository;
    private final Clock clock;

    public HolidayYearGuard(HolidayRepository holidayRepository, Clock clock) {
        this.holidayRepository = holidayRepository;
        this.clock = clock;
    }

    public void ensureHolidaysLoadedForTerm(Term term) {
        if (term.getStartDate() != null && term.getEndDate() != null) {
            int startYear = term.getStartDate().getYear();
            int lastRequiredYear = Math.max(startYear, Math.min(term.getEndDate().getYear(), LocalDate.now(clock).getYear() + YEARS_AHEAD_REQUIRED));
            for (int year = startYear; year <= lastRequiredYear; year++) {
                ensureHolidaysLoaded(year);
            }
        } else if (term.getYear() != null) {
            ensureHolidaysLoaded(term.getYear());
        }
    }

    public void ensureHolidaysLoaded(int year) {
        if (year < LocalDate.now(clock).getYear()) {
            return;
        }
        long loaded = holidayRepository.countByDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        if (loaded < MINIMUM_HOLIDAYS_PER_YEAR) {
            throw new HolidaysNotLoadedForYearException(year);
        }
    }
}
