package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardActaFile;
import com.red_escuela.backend.evaluation.specialist.ExamBoardActaExporter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GenerateExamBoardActaService implements GenerateExamBoardActaUseCase {

    private final ExamBoardActaExporter exporter;

    public GenerateExamBoardActaService(ExamBoardActaExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public ExamBoardActaFile generate(GenerateExamBoardActaCommand command) {
        return exporter.export(command.examBoardId(), command.requiredTribunalInstructorId());
    }
}
