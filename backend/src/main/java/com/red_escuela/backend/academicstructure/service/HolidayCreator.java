package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.CreateHolidayRequest;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.exception.HolidayAlreadyExistsException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HolidayCreator {

    private final HolidayRepository holidayRepository;

    public HolidayCreator(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public HolidayResponse create(CreateHolidayRequest request) {
        if (holidayRepository.existsByDate(request.date())) {
            throw new HolidayAlreadyExistsException(request.date());
        }
        Holiday holiday = new Holiday();
        holiday.setDate(request.date());
        holiday.setDescription(request.description());
        return HolidayResponse.from(holidayRepository.save(holiday));
    }
}
