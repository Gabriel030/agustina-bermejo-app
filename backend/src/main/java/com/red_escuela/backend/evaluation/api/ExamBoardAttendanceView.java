package com.red_escuela.backend.evaluation.api;

import java.time.LocalDate;
import java.util.List;

public record ExamBoardAttendanceView(Long examBoardId, boolean held, LocalDate examDate, String subjectName,
                                      List<ExamBoardAttendeeView> attendees) {
}
