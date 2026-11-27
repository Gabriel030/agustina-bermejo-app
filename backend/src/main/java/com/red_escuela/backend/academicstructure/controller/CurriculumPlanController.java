package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CreateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.ExpireCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramSubjectResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.ActivateCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.CreateCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.ExpireCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.GetCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.UpdateCurriculumPlanUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CurriculumPlanController {

    private final CreateCurriculumPlanUseCase createCurriculumPlanUseCase;
    private final UpdateCurriculumPlanUseCase updateCurriculumPlanUseCase;
    private final ActivateCurriculumPlanUseCase activateCurriculumPlanUseCase;
    private final ExpireCurriculumPlanUseCase expireCurriculumPlanUseCase;
    private final GetCurriculumPlanUseCase getCurriculumPlanUseCase;
    private final ProgramSubjectService programSubjectService;

    public CurriculumPlanController(CreateCurriculumPlanUseCase createCurriculumPlanUseCase,
                                    UpdateCurriculumPlanUseCase updateCurriculumPlanUseCase,
                                    ActivateCurriculumPlanUseCase activateCurriculumPlanUseCase,
                                    ExpireCurriculumPlanUseCase expireCurriculumPlanUseCase,
                                    GetCurriculumPlanUseCase getCurriculumPlanUseCase,
                                    ProgramSubjectService programSubjectService) {
        this.createCurriculumPlanUseCase = createCurriculumPlanUseCase;
        this.updateCurriculumPlanUseCase = updateCurriculumPlanUseCase;
        this.activateCurriculumPlanUseCase = activateCurriculumPlanUseCase;
        this.expireCurriculumPlanUseCase = expireCurriculumPlanUseCase;
        this.getCurriculumPlanUseCase = getCurriculumPlanUseCase;
        this.programSubjectService = programSubjectService;
    }

    @GetMapping("/programs/{programId}/curriculum-plans")
    public List<CurriculumPlanResponse> listByProgram(@PathVariable Long programId) {
        return getCurriculumPlanUseCase.listByProgram(programId);
    }

    @PostMapping("/programs/{programId}/curriculum-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CurriculumPlanResponse create(@PathVariable Long programId, @Valid @RequestBody CreateCurriculumPlanRequest request) {
        return createCurriculumPlanUseCase.createDraftVersion(programId, request);
    }

    @GetMapping("/curriculum-plans/{id}")
    public CurriculumPlanResponse get(@PathVariable Long id) {
        return getCurriculumPlanUseCase.get(id);
    }

    @PutMapping("/curriculum-plans/{id}")
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CurriculumPlanResponse update(@PathVariable Long id, @RequestBody UpdateCurriculumPlanRequest request) {
        return updateCurriculumPlanUseCase.update(id, request);
    }

    @PostMapping("/curriculum-plans/{id}/activate")
    @RequiresPermission(code = "academicstructure.change_program_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CurriculumPlanResponse activate(@PathVariable Long id) {
        return activateCurriculumPlanUseCase.activate(id);
    }

    @PostMapping("/curriculum-plans/{id}/expire")
    @RequiresPermission(code = "academicstructure.change_program_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CurriculumPlanResponse expire(@PathVariable Long id, @Valid @RequestBody ExpireCurriculumPlanRequest request) {
        return expireCurriculumPlanUseCase.expire(id, request);
    }

    @GetMapping("/curriculum-plans/{id}/subjects")
    public List<ProgramSubjectResponse> listSubjects(@PathVariable Long id) {
        return programSubjectService.listByPlan(id).stream().map(ProgramSubjectResponse::from).toList();
    }

    @PostMapping("/curriculum-plans/{id}/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public ProgramSubjectResponse addSubject(@PathVariable Long id, @Valid @RequestBody CreateProgramSubjectRequest request) {
        return ProgramSubjectResponse.from(programSubjectService.addSubjectToPlan(id, request));
    }
}
