package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Holiday;

import java.time.LocalDate;

public record HolidayResponse(Long id, LocalDate date, String description) {
    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getDescription());
    }
}
