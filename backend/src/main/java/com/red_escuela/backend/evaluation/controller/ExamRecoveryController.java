package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;
import com.red_escuela.backend.evaluation.dto.RecordExamRecoveryRequest;
import com.red_escuela.backend.evaluation.usecase.graderecovery.RecordExamRecoveryCommand;
import com.red_escuela.backend.evaluation.usecase.graderecovery.RecordExamRecoveryUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamRecoveryController {

    private final RecordExamRecoveryUseCase recordExamRecoveryUseCase;
    private final CurrentInstructorService currentInstructorService;

    public ExamRecoveryController(RecordExamRecoveryUseCase recordExamRecoveryUseCase,
                                  CurrentInstructorService currentInstructorService) {
        this.recordExamRecoveryUseCase = recordExamRecoveryUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/grade-items/{id}/recovery")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public GradeItemAuditResponse recordExamRecovery(@PathVariable Long id, @Valid @RequestBody RecordExamRecoveryRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return recordExamRecoveryUseCase.record(
                new RecordExamRecoveryCommand(id, instructor.getId(), instructor.getPerson().getId(), request));
    }
}
