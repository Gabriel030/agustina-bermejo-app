package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CorrectGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;
import com.red_escuela.backend.evaluation.usecase.gradecorrection.CorrectPublishedGradeCommand;
import com.red_escuela.backend.evaluation.usecase.gradecorrection.CorrectPublishedGradeUseCase;
import com.red_escuela.backend.evaluation.usecase.gradecorrection.ListGradeItemAuditUseCase;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GradeCorrectionController {

    private final CorrectPublishedGradeUseCase correctPublishedGradeUseCase;
    private final ListGradeItemAuditUseCase listGradeItemAuditUseCase;
    private final CurrentActorFinder currentActorFinder;

    public GradeCorrectionController(CorrectPublishedGradeUseCase correctPublishedGradeUseCase,
                                     ListGradeItemAuditUseCase listGradeItemAuditUseCase,
                                     CurrentActorFinder currentActorFinder) {
        this.correctPublishedGradeUseCase = correctPublishedGradeUseCase;
        this.listGradeItemAuditUseCase = listGradeItemAuditUseCase;
        this.currentActorFinder = currentActorFinder;
    }

    @PostMapping("/grade-items/{id}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public GradeItemAuditResponse correctPublishedGrade(@PathVariable Long id, @Valid @RequestBody CorrectGradeItemRequest request) {
        return GradeItemAuditResponse.from(correctPublishedGradeUseCase.correct(
                new CorrectPublishedGradeCommand(id, currentActorFinder.currentPersonId(), request)));
    }

    @GetMapping("/grade-items/{id}/audit")
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<GradeItemAuditResponse> listGradeItemAudit(@PathVariable Long id) {
        return listGradeItemAuditUseCase.list(id).stream().map(GradeItemAuditResponse::from).toList();
    }
}
