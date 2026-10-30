package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Transactional(readOnly = true)
public class HolidayFinder {

    private final HolidayRepository holidayRepository;

    public HolidayFinder(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByDate(date);
    }
}
