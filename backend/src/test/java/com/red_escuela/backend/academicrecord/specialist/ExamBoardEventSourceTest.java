package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.evaluation.api.StudentExamBoardReader;
import com.red_escuela.backend.evaluation.api.StudentExamBoardView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamBoardEventSourceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 10, 12, 0);

    private final StudentExamBoardReader reader = mock(StudentExamBoardReader.class);
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final ExamBoardEventSource source = new ExamBoardEventSource(reader, clock);

    private List<CalendarEvent> eventsOf(LocalDateTime examDate, String boardStatus, String registrationStatus) {
        when(reader.listForStudent(any(), any(), any())).thenReturn(List.of(
                new StudentExamBoardView(7L, "Anatomia", examDate, "Aula 1", boardStatus, registrationStatus, false)));
        return source.eventsFor(new CalendarQuery(1L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1), List.of(), List.of()));
    }

    private CalendarEventStatus singleStatus(LocalDateTime examDate, String boardStatus, String registrationStatus) {
        List<CalendarEvent> events = eventsOf(examDate, boardStatus, registrationStatus);
        assertThat(events).hasSize(1);
        return events.get(0).status();
    }

    @Test
    void upcomingScheduledBoardWithRegisteredRegistrationIsRegistered() {
        assertThat(singleStatus(NOW.plusDays(3), "SCHEDULED", "REGISTERED")).isEqualTo(CalendarEventStatus.REGISTERED);
    }

    @Test
    void boardHappeningExactlyNowIsStillRegistered() {
        assertThat(singleStatus(NOW, "SCHEDULED", "REGISTERED")).isEqualTo(CalendarEventStatus.REGISTERED);
    }

    @Test
    void gradedRegistrationIsNeutralHistory() {
        assertThat(singleStatus(NOW.plusDays(3), "SCHEDULED", "GRADED")).isEqualTo(CalendarEventStatus.SCHEDULED);
    }

    @Test
    void absentRegistrationIsNeutralHistory() {
        assertThat(singleStatus(NOW.plusDays(3), "SCHEDULED", "ABSENT")).isEqualTo(CalendarEventStatus.SCHEDULED);
    }

    @Test
    void closedBoardWithRegisteredRegistrationIsNeutralHistory() {
        assertThat(singleStatus(NOW.plusDays(3), "CLOSED", "REGISTERED")).isEqualTo(CalendarEventStatus.SCHEDULED);
    }

    @Test
    void pastBoardWithRegisteredRegistrationIsNeutralHistory() {
        assertThat(singleStatus(NOW.minusMinutes(1), "SCHEDULED", "REGISTERED")).isEqualTo(CalendarEventStatus.SCHEDULED);
    }

    @Test
    void canceledBoardWithRegistrationStaysCanceled() {
        assertThat(singleStatus(NOW.plusDays(3), "CANCELED", "REGISTERED")).isEqualTo(CalendarEventStatus.CANCELED);
    }

    @Test
    void withdrawnRegistrationOnCanceledBoardIsNotEmitted() {
        assertThat(eventsOf(NOW.plusDays(3), "CANCELED", "WITHDRAWN")).isEmpty();
    }
}
