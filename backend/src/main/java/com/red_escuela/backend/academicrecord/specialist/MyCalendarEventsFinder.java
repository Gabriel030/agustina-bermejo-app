package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.exception.CalendarProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class MyCalendarEventsFinder {

    private static final Logger log = LoggerFactory.getLogger(MyCalendarEventsFinder.class);

    private final ObjectProvider<MyCalendarProvider> providers;

    public MyCalendarEventsFinder(ObjectProvider<MyCalendarProvider> providers) {
        this.providers = providers;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnWhenNoProviderIsRegistered() {
        if (providers.stream().findAny().isEmpty()) {
            log.warn("No MyCalendarProvider bean is registered: calendar feed and export answer 503 until one is provided");
        }
    }

    public List<CalendarEvent> findEvents(Long studentId, LocalDate from, LocalDate to) {
        MyCalendarProvider provider = providers.getIfAvailable();
        if (provider == null) {
            throw new CalendarProviderUnavailableException();
        }
        return provider.eventsOf(studentId, from, to);
    }
}
