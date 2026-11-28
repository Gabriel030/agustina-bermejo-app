package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ChangeProgramEnrollmentPlanRequest;
import com.red_escuela.backend.enrollment.dto.OwnSubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentPlanChangeResponse;
import com.red_escuela.backend.enrollment.usecase.programenrollment.ChangeProgramEnrollmentPlanCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollment.ChangeProgramEnrollmentPlanUseCase;
import com.red_escuela.backend.enrollment.dto.RecordSubjectEquivalenceRequest;
import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceStatusFilter;
import com.red_escuela.backend.enrollment.dto.VoidSubjectEquivalenceRequest;
import com.red_escuela.backend.enrollment.usecase.equivalence.ListOwnSubjectEquivalencesUseCase;
import com.red_escuela.backend.enrollment.usecase.equivalence.ListSubjectEquivalencesUseCase;
import com.red_escuela.backend.enrollment.usecase.equivalence.RecordSubjectEquivalenceCommand;
import com.red_escuela.backend.enrollment.usecase.equivalence.RecordSubjectEquivalenceUseCase;
import com.red_escuela.backend.enrollment.usecase.equivalence.VoidSubjectEquivalenceCommand;
import com.red_escuela.backend.enrollment.usecase.equivalence.VoidSubjectEquivalenceUseCase;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SubjectEquivalenceController {
    private final ListSubjectEquivalencesUseCase listSubjectEquivalencesUseCase;
    private final ListOwnSubjectEquivalencesUseCase listOwnSubjectEquivalencesUseCase;
    private final RecordSubjectEquivalenceUseCase recordSubjectEquivalenceUseCase;
    private final VoidSubjectEquivalenceUseCase voidSubjectEquivalenceUseCase;
    private final ChangeProgramEnrollmentPlanUseCase changeProgramEnrollmentPlanUseCase;
    private final CurrentStudentService currentStudentService;

    public SubjectEquivalenceController(ListSubjectEquivalencesUseCase listSubjectEquivalencesUseCase,
                                        ListOwnSubjectEquivalencesUseCase listOwnSubjectEquivalencesUseCase,
                                        RecordSubjectEquivalenceUseCase recordSubjectEquivalenceUseCase,
                                        VoidSubjectEquivalenceUseCase voidSubjectEquivalenceUseCase,
                                        ChangeProgramEnrollmentPlanUseCase changeProgramEnrollmentPlanUseCase,
                                        CurrentStudentService currentStudentService) {
        this.listSubjectEquivalencesUseCase = listSubjectEquivalencesUseCase;
        this.listOwnSubjectEquivalencesUseCase = listOwnSubjectEquivalencesUseCase;
        this.recordSubjectEquivalenceUseCase = recordSubjectEquivalenceUseCase;
        this.voidSubjectEquivalenceUseCase = voidSubjectEquivalenceUseCase;
        this.changeProgramEnrollmentPlanUseCase = changeProgramEnrollmentPlanUseCase;
        this.currentStudentService = currentStudentService;
    }

    @GetMapping("/program-enrollments/{id}/equivalences")
    @RequiresPermission(code = "enrollment.read_subject_equivalences", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<SubjectEquivalenceResponse> list(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "ACTIVE") SubjectEquivalenceStatusFilter status) {
        return listSubjectEquivalencesUseCase.list(id, status);
    }

    @GetMapping("/me/program-enrollments/{id}/equivalences")
    public List<OwnSubjectEquivalenceResponse> listOwn(@PathVariable Long id) {
        return listOwnSubjectEquivalencesUseCase.list(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @PostMapping("/program-enrollments/{id}/equivalences")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "enrollment.record_subject_equivalence", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public SubjectEquivalenceResponse record(@PathVariable Long id, @Valid @RequestBody RecordSubjectEquivalenceRequest request) {
        return recordSubjectEquivalenceUseCase.record(new RecordSubjectEquivalenceCommand(id, request));
    }

    @PostMapping("/program-enrollments/{id}/plan-change")
    @RequiresPermission(code = "enrollment.record_subject_equivalence", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramEnrollmentPlanChangeResponse changePlan(@PathVariable Long id,
                                                          @Valid @RequestBody ChangeProgramEnrollmentPlanRequest request) {
        return changeProgramEnrollmentPlanUseCase.change(new ChangeProgramEnrollmentPlanCommand(id, request));
    }

    @PostMapping("/program-enrollments/{id}/equivalences/{equivalenceId}/void")
    @RequiresPermission(code = "enrollment.void_subject_equivalence", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public SubjectEquivalenceResponse voidEquivalence(@PathVariable Long id, @PathVariable Long equivalenceId,
                                                      @Valid @RequestBody VoidSubjectEquivalenceRequest request) {
        return voidSubjectEquivalenceUseCase.voidEquivalence(new VoidSubjectEquivalenceCommand(id, equivalenceId, request.reason()));
    }
}
