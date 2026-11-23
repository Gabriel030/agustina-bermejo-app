package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;

import java.util.List;

public interface CalendarEventSource {

    List<CalendarEvent> eventsFor(CalendarQuery query);
}
