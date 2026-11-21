package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.academicrecord.dto.CalendarEventType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HolidayEventSource implements CalendarEventSource {

    @Override
    public List<CalendarEvent> eventsFor(CalendarQuery query) {
        return query.holidays().stream()
                .map(holiday -> new CalendarEvent("holiday-" + holiday.date(), CalendarEventType.HOLIDAY, holiday.date(),
                        null, null, holiday.name(), null, null, null, CalendarEventStatus.SCHEDULED, null, null))
                .toList();
    }
}
