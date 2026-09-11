package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.ApproveSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.ApproveSectionEvaluationConfigUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.GetSectionEvaluationConfigForStaffUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.ListSectionEvaluationConfigsUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.RejectSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.RejectSectionEvaluationConfigUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SectionEvaluationConfigController {

    private final ListSectionEvaluationConfigsUseCase listSectionEvaluationConfigsUseCase;
    private final GetSectionEvaluationConfigForStaffUseCase getSectionEvaluationConfigForStaffUseCase;
    private final ApproveSectionEvaluationConfigUseCase approveSectionEvaluationConfigUseCase;
    private final RejectSectionEvaluationConfigUseCase rejectSectionEvaluationConfigUseCase;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final CurrentPersonService currentPersonService;

    public SectionEvaluationConfigController(
            ListSectionEvaluationConfigsUseCase listSectionEvaluationConfigsUseCase,
            GetSectionEvaluationConfigForStaffUseCase getSectionEvaluationConfigForStaffUseCase,
            ApproveSectionEvaluationConfigUseCase approveSectionEvaluationConfigUseCase,
            RejectSectionEvaluationConfigUseCase rejectSectionEvaluationConfigUseCase,
            SectionEvaluationConfigInstructorAccess instructorAccess,
            CurrentPersonService currentPersonService) {
        this.listSectionEvaluationConfigsUseCase = listSectionEvaluationConfigsUseCase;
        this.getSectionEvaluationConfigForStaffUseCase = getSectionEvaluationConfigForStaffUseCase;
        this.approveSectionEvaluationConfigUseCase = approveSectionEvaluationConfigUseCase;
        this.rejectSectionEvaluationConfigUseCase = rejectSectionEvaluationConfigUseCase;
        this.instructorAccess = instructorAccess;
        this.currentPersonService = currentPersonService;
    }

    @GetMapping("/section-evaluation-configs")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<SectionEvaluationConfigResponse> list(@RequestParam(required = false) SectionEvaluationConfigStatus status) {
        return listSectionEvaluationConfigsUseCase.list(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/section-evaluation-configs/{sectionId}")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEvaluationConfigResponse get(@PathVariable Long sectionId) {
        return toResponse(getSectionEvaluationConfigForStaffUseCase.get(sectionId));
    }

    @PostMapping("/section-evaluation-configs/{sectionId}/approve")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEvaluationConfigResponse approve(@PathVariable Long sectionId) {
        Person actor = currentPersonService.resolveCurrentPerson();
        return toResponse(approveSectionEvaluationConfigUseCase.approve(new ApproveSectionEvaluationConfigCommand(sectionId, actor)));
    }

    @PostMapping("/section-evaluation-configs/{sectionId}/reject")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEvaluationConfigResponse reject(
            @PathVariable Long sectionId, @Valid @RequestBody RejectSectionEvaluationConfigRequest request) {
        Person actor = currentPersonService.resolveCurrentPerson();
        return toResponse(rejectSectionEvaluationConfigUseCase.reject(new RejectSectionEvaluationConfigCommand(sectionId, actor, request)));
    }

    private SectionEvaluationConfigResponse toResponse(SectionEvaluationConfig config) {
        return SectionEvaluationConfigResponse.from(config, instructorAccess.listInstructors(config.getSubjectSection().getId()));
    }
}
