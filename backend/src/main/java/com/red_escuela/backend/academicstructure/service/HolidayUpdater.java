package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateHolidayRequest;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.exception.HolidayAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.HolidayNotFoundException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HolidayUpdater {

    private final HolidayRepository holidayRepository;

    public HolidayUpdater(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public HolidayResponse update(Long id, UpdateHolidayRequest request) {
        Holiday holiday = holidayRepository.findById(id).orElseThrow(() -> new HolidayNotFoundException(id));
        if (holidayRepository.existsByDateAndIdNot(request.date(), id)) {
            throw new HolidayAlreadyExistsException(request.date());
        }
        holiday.setDate(request.date());
        holiday.setName(request.name());
        holiday.setType(request.type());
        holiday.setDescription(request.description());
        return HolidayResponse.from(holidayRepository.saveAndFlush(holiday));
    }
}
