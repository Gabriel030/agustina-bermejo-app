package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicstructure.api.HolidayFinder;
import com.red_escuela.backend.enrollment.api.ActiveSectionView;
import com.red_escuela.backend.enrollment.api.StudentActiveSectionsReader;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class StudentCalendarProvider implements MyCalendarProvider {

    private static final Comparator<CalendarEvent> EVENT_ORDER = Comparator
            .comparing(CalendarEvent::date)
            .thenComparing(CalendarEvent::startTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CalendarEvent::title)
            .thenComparing(CalendarEvent::id);

    private final StudentActiveSectionsReader activeSectionsReader;
    private final HolidayFinder holidayFinder;
    private final List<CalendarEventSource> sources;

    public StudentCalendarProvider(StudentActiveSectionsReader activeSectionsReader, HolidayFinder holidayFinder,
                                   List<CalendarEventSource> sources) {
        this.activeSectionsReader = activeSectionsReader;
        this.holidayFinder = holidayFinder;
        this.sources = sources;
    }

    @Override
    public List<CalendarEvent> eventsOf(Long studentId, LocalDate from, LocalDate to) {
        return calendarOf(studentId, from, to).events();
    }

    public StudentCalendar calendarOf(Long studentId, LocalDate from, LocalDate to) {
        List<ActiveSectionView> activeSections = activeSectionsReader.listActiveSectionsOfStudent(studentId);
        CalendarQuery query = new CalendarQuery(studentId, from, to, activeSections, holidayFinder.findBetween(from, to));
        List<CalendarEvent> events = sources.stream()
                .flatMap(source -> source.eventsFor(query).stream())
                .sorted(EVENT_ORDER)
                .toList();
        return new StudentCalendar(!activeSections.isEmpty(), events);
    }
}
