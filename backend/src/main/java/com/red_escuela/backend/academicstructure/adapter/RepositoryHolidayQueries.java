package com.red_escuela.backend.academicstructure.adapter;

import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.academicstructure.usecase.HolidayQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class RepositoryHolidayQueries implements HolidayQueries {

    private final HolidayRepository holidayRepository;

    public RepositoryHolidayQueries(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Override
    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByDate(date);
    }
}
