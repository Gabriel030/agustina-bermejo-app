package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradesRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradesResponse;
import com.red_escuela.backend.evaluation.usecase.examboard.RecordExamBoardFinalGradesCommand;
import com.red_escuela.backend.evaluation.usecase.examboard.RecordExamBoardFinalGradesUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamBoardFinalGradesController {

    private final RecordExamBoardFinalGradesUseCase recordExamBoardFinalGradesUseCase;
    private final CurrentInstructorService currentInstructorService;

    public ExamBoardFinalGradesController(RecordExamBoardFinalGradesUseCase recordExamBoardFinalGradesUseCase,
                                          CurrentInstructorService currentInstructorService) {
        this.recordExamBoardFinalGradesUseCase = recordExamBoardFinalGradesUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @PutMapping("/exam-boards/{id}/final-grades")
    @PreAuthorize("hasRole('DOCENTE')")
    public ExamBoardFinalGradesResponse recordFinalGrades(@PathVariable Long id,
                                                          @Valid @RequestBody ExamBoardFinalGradesRequest request) {
        return recordExamBoardFinalGradesUseCase.record(new RecordExamBoardFinalGradesCommand(
                id, currentInstructorService.resolveCurrentInstructor().getId(), request.items()));
    }
}
