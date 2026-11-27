package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.entity.Holiday;

import java.time.LocalDate;
import java.util.List;

public record HolidayResponse(Long id, LocalDate date, String name, HolidayType type, String description,
                              List<LocalDate> attendanceAffectedDates) {
    public static HolidayResponse from(Holiday holiday) {
        return from(holiday, List.of());
    }

    public static HolidayResponse from(Holiday holiday, List<LocalDate> attendanceAffectedDates) {
        String description = holiday.getDescription() == null ? "" : holiday.getDescription();
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getName(), holiday.getType(), description,
                List.copyOf(attendanceAffectedDates));
    }
}
