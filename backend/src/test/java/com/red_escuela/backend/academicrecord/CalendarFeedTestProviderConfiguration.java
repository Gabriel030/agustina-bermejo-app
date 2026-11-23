package com.red_escuela.backend.academicrecord;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.academicrecord.dto.CalendarEventType;
import com.red_escuela.backend.academicrecord.specialist.MyCalendarProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class CalendarFeedTestProviderConfiguration {

    static final Map<Long, List<CalendarEvent>> EVENTS_BY_STUDENT = new ConcurrentHashMap<>();
    static final Map<Long, LocalDate[]> LAST_RANGE_BY_STUDENT = new ConcurrentHashMap<>();

    static CalendarEvent classEvent(String id, String title, CalendarEventStatus status) {
        return new CalendarEvent(id, CalendarEventType.CLASS, LocalDate.now(), LocalTime.of(18, 0), LocalTime.of(20, 0),
                title, "Materia", "Comisión", "Aula 1", status, null, null);
    }

    @Bean
    @Primary
    MyCalendarProvider testMyCalendarProvider() {
        return (studentId, from, to) -> {
            LAST_RANGE_BY_STUDENT.put(studentId, new LocalDate[] {from, to});
            return EVENTS_BY_STUDENT.getOrDefault(studentId, List.of());
        };
    }
}
