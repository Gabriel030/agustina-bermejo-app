package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.entity.Holiday;

import java.time.LocalDate;

public record HolidayResponse(Long id, LocalDate date, String name, HolidayType type, String description) {
    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getName(), holiday.getType(),
                holiday.getDescription());
    }
}
