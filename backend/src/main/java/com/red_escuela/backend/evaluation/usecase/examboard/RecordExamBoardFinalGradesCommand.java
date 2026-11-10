package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradeEntryRequest;

import java.util.List;

public record RecordExamBoardFinalGradesCommand(
        Long examBoardId, Long instructorId, List<ExamBoardFinalGradeEntryRequest> entries) {
}
