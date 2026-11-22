package com.red_escuela.backend.academicrecord.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarEvent(
        String id,
        CalendarEventType type,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        String title,
        String subjectName,
        String sectionName,
        String place,
        CalendarEventStatus status,
        String reason,
        String link) {
}
