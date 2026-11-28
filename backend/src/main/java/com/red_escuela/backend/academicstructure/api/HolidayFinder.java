package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class HolidayFinder {

    private final HolidayRepository holidayRepository;

    public HolidayFinder(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByDateAndScopeIn(date, HolidayScope.AFFECTING_CLASSES);
    }

    public List<HolidayView> findBetween(LocalDate from, LocalDate to) {
        return holidayRepository.findByDateBetweenAndScopeIn(from, to, HolidayScope.AFFECTING_CLASSES).stream()
                .sorted(Comparator.comparing(Holiday::getDate))
                .map(holiday -> new HolidayView(holiday.getDate(), holiday.getName(), holiday.getType().name()))
                .toList();
    }
}
