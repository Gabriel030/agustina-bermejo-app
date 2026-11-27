package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateHolidayRequest;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.exception.HolidayAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.HolidayNotFoundException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import com.red_escuela.backend.academicstructure.api.HolidayAffectedItem;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HolidayUpdater {

    private final HolidayRepository holidayRepository;
    private final HolidayImpactFinder impactFinder;

    public HolidayUpdater(HolidayRepository holidayRepository, HolidayImpactFinder impactFinder) {
        this.holidayRepository = holidayRepository;
        this.impactFinder = impactFinder;
    }

    @Transactional
    public HolidayResponse update(Long id, UpdateHolidayRequest request) {
        Holiday holiday = holidayRepository.findById(id).orElseThrow(() -> new HolidayNotFoundException(id));
        if (holidayRepository.existsByDateAndIdNot(request.date(), id)) {
            throw new HolidayAlreadyExistsException(request.date());
        }
        LocalDate previousDate = holiday.getDate();
        holiday.setDate(request.date());
        holiday.setName(request.name());
        holiday.setType(request.type());
        holiday.setDescription(request.description());
        Optional.ofNullable(request.scope()).ifPresent(holiday::setScope);
        Holiday saved = holidayRepository.saveAndFlush(holiday);
        List<HolidayAffectedItem> affected = Stream.concat(
                impactFinder.findAffectedBy(saved).stream(),
                previousDate.equals(saved.getDate()) ? Stream.<HolidayAffectedItem>empty() : impactFinder.findAffectedOn(previousDate).stream()).toList();
        return HolidayResponse.from(saved, affected);
    }
}
