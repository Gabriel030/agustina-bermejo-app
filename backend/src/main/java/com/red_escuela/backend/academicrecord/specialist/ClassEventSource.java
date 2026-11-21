package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.academicrecord.dto.CalendarEventType;
import com.red_escuela.backend.academicstructure.api.HolidayView;
import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.api.TermPeriodFinder;
import com.red_escuela.backend.academicstructure.api.TermPeriodView;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.attendance.api.CanceledClassSessionFinder;
import com.red_escuela.backend.attendance.api.CanceledClassView;
import com.red_escuela.backend.enrollment.api.ActiveSectionView;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClassEventSource implements CalendarEventSource {

    private final SectionScheduleFinder scheduleFinder;
    private final TermPeriodFinder termPeriodFinder;
    private final CanceledClassSessionFinder canceledClassFinder;

    public ClassEventSource(SectionScheduleFinder scheduleFinder, TermPeriodFinder termPeriodFinder,
                            CanceledClassSessionFinder canceledClassFinder) {
        this.scheduleFinder = scheduleFinder;
        this.termPeriodFinder = termPeriodFinder;
        this.canceledClassFinder = canceledClassFinder;
    }

    @Override
    public List<CalendarEvent> eventsFor(CalendarQuery query) {
        if (query.activeSections().isEmpty()) {
            return List.of();
        }
        Map<Long, TermPeriodView> periods = termPeriodFinder.findPeriods(query.activeSections().stream()
                .map(ActiveSectionView::termId).filter(termId -> termId != null).collect(Collectors.toSet()));
        Set<LocalDate> holidayDates = query.holidays().stream().map(HolidayView::date).collect(Collectors.toSet());
        List<CalendarEvent> events = new ArrayList<>();
        for (SectionKind kind : SectionKind.values()) {
            events.addAll(eventsOfKind(kind, query, periods, holidayDates));
        }
        return events;
    }

    private List<CalendarEvent> eventsOfKind(SectionKind kind, CalendarQuery query, Map<Long, TermPeriodView> periods,
                                             Set<LocalDate> holidayDates) {
        List<ActiveSectionView> sections = query.activeSections().stream().filter(section -> section.kind() == kind).toList();
        if (sections.isEmpty()) {
            return List.of();
        }
        List<Long> sectionIds = sections.stream().map(ActiveSectionView::sectionId).toList();
        Map<Long, List<SectionSlotView>> slotsBySection = kind == SectionKind.PROGRAM
                ? scheduleFinder.findProgramSectionSlots(sectionIds)
                : scheduleFinder.findCourseSectionSlots(sectionIds);
        Set<CanceledClassView> canceled = Set.copyOf(canceledClassFinder.findCanceled(kind, sectionIds, query.from(), query.to()));
        List<CalendarEvent> events = new ArrayList<>();
        for (ActiveSectionView section : sections) {
            TermPeriodView period = section.termId() == null ? null : periods.get(section.termId());
            if (period == null) {
                continue;
            }
            LocalDate start = latest(query.from(), period.startDate().toLocalDate());
            LocalDate end = earliest(query.to(), period.endDate().toLocalDate());
            List<SectionSlotView> slots = slotsBySection.getOrDefault(section.sectionId(), List.of());
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                if (holidayDates.contains(day)) {
                    continue;
                }
                for (SectionSlotView slot : slots) {
                    if (slot.dayOfWeek() == day.getDayOfWeek()) {
                        boolean isCanceled = canceled.contains(new CanceledClassView(section.sectionId(), day));
                        events.add(toEvent(kind, section, slot, day, isCanceled));
                    }
                }
            }
        }
        return events;
    }

    private CalendarEvent toEvent(SectionKind kind, ActiveSectionView section, SectionSlotView slot, LocalDate day,
                                  boolean canceled) {
        String id = "class-" + kind.name().toLowerCase() + "-" + section.sectionId() + "-" + day + "-" + slot.startTime();
        return new CalendarEvent(id, CalendarEventType.CLASS, day, slot.startTime(), slot.endTime(), section.subjectName(),
                section.subjectName(), section.sectionName(), slot.classroomName(),
                canceled ? CalendarEventStatus.CANCELED : CalendarEventStatus.SCHEDULED, null, null);
    }

    private static LocalDate latest(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate earliest(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
