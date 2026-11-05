package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.RescheduleExamBoardRequest;

public record RescheduleExamBoardCommand(Long examBoardId, RescheduleExamBoardRequest request) {
}
