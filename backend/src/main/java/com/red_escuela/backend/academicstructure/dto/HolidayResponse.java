package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.api.HolidayAffectedItem;
import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.entity.Holiday;

import java.time.LocalDate;
import java.util.List;

public record HolidayResponse(Long id, LocalDate date, String name, HolidayType type, String description,
                              HolidayScope scope, List<HolidayAffectedItem> affected) {
    public static HolidayResponse from(Holiday holiday) {
        return from(holiday, List.of());
    }

    public static HolidayResponse from(Holiday holiday, List<HolidayAffectedItem> affected) {
        String description = holiday.getDescription() == null ? "" : holiday.getDescription();
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getName(), holiday.getType(), description,
                holiday.getScope(), affected);
    }
}
