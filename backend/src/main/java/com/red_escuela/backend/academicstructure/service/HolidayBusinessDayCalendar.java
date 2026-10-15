package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class HolidayBusinessDayCalendar implements BusinessDayCalendar {
    private final HolidayRepository holidayRepository;

    public HolidayBusinessDayCalendar(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBusinessDay(LocalDate date) {
        return !isWeekend(date) && !holidayRepository.existsByDate(date);
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
