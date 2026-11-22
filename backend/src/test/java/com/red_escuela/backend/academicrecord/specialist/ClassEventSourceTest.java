package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.api.TermPeriodFinder;
import com.red_escuela.backend.academicstructure.api.TermPeriodView;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.attendance.api.CanceledClassSessionFinder;
import com.red_escuela.backend.enrollment.api.ActiveSectionView;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassEventSourceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    private final SectionScheduleFinder scheduleFinder = mock(SectionScheduleFinder.class);
    private final TermPeriodFinder termPeriodFinder = mock(TermPeriodFinder.class);
    private final CanceledClassSessionFinder canceledFinder = mock(CanceledClassSessionFinder.class);
    private final ClassEventSource source = new ClassEventSource(scheduleFinder, termPeriodFinder, canceledFinder);

    private List<CalendarEvent> eventsWithSlots(List<SectionSlotView> slots) {
        when(termPeriodFinder.findPeriods(any())).thenReturn(Map.of(5L,
                new TermPeriodView(5L, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 6, 30, 0, 0))));
        when(scheduleFinder.findProgramSectionSlots(any(Collection.class))).thenReturn(Map.of(10L, slots));
        when(canceledFinder.findCanceled(any(), any(), any(), any())).thenReturn(List.of());
        ActiveSectionView section = new ActiveSectionView(SectionKind.PROGRAM, 10L, "Comision A", "Anatomia", 5L);
        return source.eventsFor(new CalendarQuery(1L, MONDAY, MONDAY, List.of(section), List.of()));
    }

    @Test
    void twoSlotsAtTheSameTimeInDifferentRoomsProduceDistinctStableIds() {
        SectionSlotView roomB = new SectionSlotView(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0), "Aula B");
        SectionSlotView roomA = new SectionSlotView(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0), "Aula A");

        List<CalendarEvent> forward = eventsWithSlots(List.of(roomB, roomA));
        List<CalendarEvent> reversed = eventsWithSlots(List.of(roomA, roomB));

        assertThat(forward).hasSize(2);
        assertThat(forward).extracting(CalendarEvent::id).doesNotHaveDuplicates();
        assertThat(forward).extracting(CalendarEvent::id).containsExactly(
                "class-program-10-2026-03-02-18:00", "class-program-10-2026-03-02-18:00-1");
        assertThat(forward).extracting(CalendarEvent::place).containsExactly("Aula A", "Aula B");
        assertThat(reversed).extracting(CalendarEvent::id).containsExactlyElementsOf(
                forward.stream().map(CalendarEvent::id).toList());
        assertThat(reversed).extracting(CalendarEvent::place).containsExactly("Aula A", "Aula B");
    }

    @Test
    void singleSlotKeepsTheBaseIdFormat() {
        List<CalendarEvent> events = eventsWithSlots(List.of(
                new SectionSlotView(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0), "Aula A")));

        assertThat(events).extracting(CalendarEvent::id).containsExactly("class-program-10-2026-03-02-18:00");
    }

    @Test
    void slotsAtDifferentTimesNeverGetADiscriminator() {
        List<CalendarEvent> events = eventsWithSlots(List.of(
                new SectionSlotView(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0), "Aula A"),
                new SectionSlotView(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(20, 0), "Aula B")));

        assertThat(events).extracting(CalendarEvent::id).containsExactly(
                "class-program-10-2026-03-02-18:00", "class-program-10-2026-03-02-19:00");
    }
}
