package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;

import java.time.LocalDate;
import java.util.List;

public interface MyCalendarProvider {

    List<CalendarEvent> eventsOf(Long studentId, LocalDate from, LocalDate to);
}
