package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.HolidayAttendanceConflictPort;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateHolidayRequest;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.exception.HolidayAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.HolidayNotFoundException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class HolidayUpdater {

    private final HolidayRepository holidayRepository;
    private final HolidayAttendanceConflictPort attendanceConflictPort;

    public HolidayUpdater(HolidayRepository holidayRepository, HolidayAttendanceConflictPort attendanceConflictPort) {
        this.holidayRepository = holidayRepository;
        this.attendanceConflictPort = attendanceConflictPort;
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
        Holiday saved = holidayRepository.saveAndFlush(holiday);
        return HolidayResponse.from(saved, attendanceConflictPort.findDatesWithRecordedAttendance(List.of(saved.getDate())));
    }
}
