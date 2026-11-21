package com.red_escuela.backend.academicrecord.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarEvent(
        String id,
        CalendarEventType type,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String title,
        String subjectName,
        String sectionName,
        String place,
        CalendarEventStatus status,
        String reason,
        String link) {
}
