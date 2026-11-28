package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.dto.CreatePreceptorAssignmentRequest;
import com.red_escuela.backend.identity.dto.PreceptorAssignmentResponse;
import com.red_escuela.backend.identity.dto.AssignPreceptorCommand;
import com.red_escuela.backend.identity.usecase.preceptorassignment.AssignPreceptorUseCase;
import com.red_escuela.backend.identity.dto.ListPreceptorAssignmentsCommand;
import com.red_escuela.backend.identity.usecase.preceptorassignment.ListPreceptorAssignmentsUseCase;
import com.red_escuela.backend.identity.dto.PreceptorAssignmentResult;
import com.red_escuela.backend.identity.usecase.preceptorassignment.UnassignPreceptorUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/preceptor-assignments")
public class PreceptorAssignmentController {

    private static final String PERMISSION = "academicstructure.manage_preceptor_assignments";
    private static final String LEGACY_ROLES = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR";

    private final ListPreceptorAssignmentsUseCase listPreceptorAssignmentsUseCase;
    private final AssignPreceptorUseCase assignPreceptorUseCase;
    private final UnassignPreceptorUseCase unassignPreceptorUseCase;
    private final CurrentActorFinder currentActorFinder;

    public PreceptorAssignmentController(ListPreceptorAssignmentsUseCase listPreceptorAssignmentsUseCase,
                                         AssignPreceptorUseCase assignPreceptorUseCase,
                                         UnassignPreceptorUseCase unassignPreceptorUseCase,
                                         CurrentActorFinder currentActorFinder) {
        this.listPreceptorAssignmentsUseCase = listPreceptorAssignmentsUseCase;
        this.assignPreceptorUseCase = assignPreceptorUseCase;
        this.unassignPreceptorUseCase = unassignPreceptorUseCase;
        this.currentActorFinder = currentActorFinder;
    }

    @GetMapping
    @RequiresPermission(code = PERMISSION, legacyRoles = LEGACY_ROLES)
    public List<PreceptorAssignmentResponse> list(@RequestParam(required = false) Long subjectSectionId,
                                                  @RequestParam(required = false) Long courseSectionId) {
        return listPreceptorAssignmentsUseCase.list(new ListPreceptorAssignmentsCommand(subjectSectionId, courseSectionId))
                .stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = PERMISSION, legacyRoles = LEGACY_ROLES)
    public PreceptorAssignmentResponse assign(@Valid @RequestBody CreatePreceptorAssignmentRequest request) {
        return toResponse(assignPreceptorUseCase.assign(new AssignPreceptorCommand(request.personId(),
                request.subjectSectionId(), request.courseSectionId(), currentActorFinder.currentPersonId())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = PERMISSION, legacyRoles = LEGACY_ROLES)
    public void unassign(@PathVariable Long id) {
        unassignPreceptorUseCase.unassign(id);
    }

    private PreceptorAssignmentResponse toResponse(PreceptorAssignmentResult result) {
        return PreceptorAssignmentResponse.from(result.assignment(), result.person());
    }
}
