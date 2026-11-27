package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardActaFile;

public interface GenerateExamBoardActaUseCase {
    ExamBoardActaFile generate(GenerateExamBoardActaCommand command);
}
