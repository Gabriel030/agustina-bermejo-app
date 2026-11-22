package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CorrectGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;
import com.red_escuela.backend.evaluation.usecase.gradecorrection.CorrectPublishedGradeCommand;
import com.red_escuela.backend.evaluation.usecase.gradecorrection.CorrectPublishedGradeUseCase;
import com.red_escuela.backend.evaluation.usecase.gradecorrection.ListGradeItemAuditUseCase;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    @RequiresPermission(code = "evaluation.correct_published_grade", legacyRoles = "ROLE_DIRECTOR")
    public GradeItemAuditResponse correctPublishedGrade(@PathVariable Long id, @Valid @RequestBody CorrectGradeItemRequest request) {
        return correctPublishedGradeUseCase.correct(
                new CorrectPublishedGradeCommand(id, currentActorFinder.currentPersonId(), request));
    }

    @GetMapping("/grade-items/{id}/audit")
    @RequiresPermission(code = "evaluation.read_grade_oversight", legacyRoles = "ROLE_DIRECTOR")
    public List<GradeItemAuditResponse> listGradeItemAudit(@PathVariable Long id) {
        return listGradeItemAuditUseCase.list(id);
    }
}
