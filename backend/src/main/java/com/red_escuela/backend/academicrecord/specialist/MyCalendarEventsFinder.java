package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class MyCalendarEventsFinder {

    private final ObjectProvider<MyCalendarProvider> providers;

    public MyCalendarEventsFinder(ObjectProvider<MyCalendarProvider> providers) {
        this.providers = providers;
    }

    public List<CalendarEvent> findEvents(Long studentId, LocalDate from, LocalDate to) {
        MyCalendarProvider provider = providers.getIfAvailable();
        return provider == null ? List.of() : provider.eventsOf(studentId, from, to);
    }
}
