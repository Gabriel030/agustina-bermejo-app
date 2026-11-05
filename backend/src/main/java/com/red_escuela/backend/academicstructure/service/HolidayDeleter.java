package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.HolidayNotFoundException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HolidayDeleter {

    private final HolidayRepository holidayRepository;

    public HolidayDeleter(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public void delete(Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new HolidayNotFoundException(id);
        }
        holidayRepository.deleteById(id);
    }
}
