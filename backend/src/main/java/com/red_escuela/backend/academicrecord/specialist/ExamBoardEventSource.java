package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.academicrecord.dto.CalendarEventType;
import com.red_escuela.backend.evaluation.api.StudentExamBoardReader;
import com.red_escuela.backend.evaluation.api.StudentExamBoardView;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ExamBoardEventSource implements CalendarEventSource {

    private static final String BOARD_SCHEDULED = "SCHEDULED";
    private static final String BOARD_CANCELED = "CANCELED";
    private static final String REGISTRATION_WITHDRAWN = "WITHDRAWN";
    private static final String EXAM_BOARDS_LINK = "/alumno/mis-carreras";

    private final StudentExamBoardReader examBoardReader;
    private final Clock clock;

    public ExamBoardEventSource(StudentExamBoardReader examBoardReader, Clock clock) {
        this.examBoardReader = examBoardReader;
        this.clock = clock;
    }

    @Override
    public List<CalendarEvent> eventsFor(CalendarQuery query) {
        LocalDateTime now = LocalDateTime.now(clock);
        return examBoardReader.listForStudent(query.studentId(), query.from(), query.to()).stream()
                .flatMap(view -> statusOf(view, now).map(status -> toEvent(view, status)).stream())
                .toList();
    }

    private Optional<CalendarEventStatus> statusOf(StudentExamBoardView view, LocalDateTime now) {
        boolean hadRegistration = view.registrationStatus() != null
                && !REGISTRATION_WITHDRAWN.equals(view.registrationStatus());
        if (BOARD_CANCELED.equals(view.boardStatus())) {
            return hadRegistration ? Optional.of(CalendarEventStatus.CANCELED) : Optional.empty();
        }
        if (hadRegistration) {
            return Optional.of(CalendarEventStatus.REGISTERED);
        }
        boolean open = BOARD_SCHEDULED.equals(view.boardStatus()) && view.registrationOpen()
                && !view.examDateTime().isBefore(now);
        return open ? Optional.of(CalendarEventStatus.OPEN_FOR_REGISTRATION) : Optional.empty();
    }

    private CalendarEvent toEvent(StudentExamBoardView view, CalendarEventStatus status) {
        return new CalendarEvent("exam-" + view.boardId(), CalendarEventType.EXAM_BOARD, view.examDateTime().toLocalDate(),
                view.examDateTime().toLocalTime(), null, "Mesa de examen: " + view.subjectName(), view.subjectName(),
                null, view.classroom(), status, null, EXAM_BOARDS_LINK);
    }
}
