package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.GetSectionEvaluationConfigForStaffUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ListSectionEvaluationConfigsUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.RejectSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.RejectSectionEvaluationConfigUseCase;
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
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;

    public SectionEvaluationConfigController(
            ListSectionEvaluationConfigsUseCase listSectionEvaluationConfigsUseCase,
            GetSectionEvaluationConfigForStaffUseCase getSectionEvaluationConfigForStaffUseCase,
            ApproveSectionEvaluationConfigUseCase approveSectionEvaluationConfigUseCase,
            RejectSectionEvaluationConfigUseCase rejectSectionEvaluationConfigUseCase,
            SectionEvaluationConfigInstructorAccessChecker instructorAccess) {
        this.listSectionEvaluationConfigsUseCase = listSectionEvaluationConfigsUseCase;
        this.getSectionEvaluationConfigForStaffUseCase = getSectionEvaluationConfigForStaffUseCase;
        this.approveSectionEvaluationConfigUseCase = approveSectionEvaluationConfigUseCase;
        this.rejectSectionEvaluationConfigUseCase = rejectSectionEvaluationConfigUseCase;
        this.instructorAccess = instructorAccess;
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
    @PreAuthorize("hasRole('DIRECTOR')")
    public SectionEvaluationConfigResponse approve(@PathVariable Long sectionId) {
        return toResponse(approveSectionEvaluationConfigUseCase.approve(new ApproveSectionEvaluationConfigCommand(sectionId)));
    }

    @PostMapping("/section-evaluation-configs/{sectionId}/reject")
    @PreAuthorize("hasRole('DIRECTOR')")
    public SectionEvaluationConfigResponse reject(
            @PathVariable Long sectionId, @Valid @RequestBody RejectSectionEvaluationConfigRequest request) {
        return toResponse(rejectSectionEvaluationConfigUseCase.reject(new RejectSectionEvaluationConfigCommand(sectionId, request)));
    }

    private SectionEvaluationConfigResponse toResponse(SectionEvaluationConfig config) {
        return SectionEvaluationConfigResponse.from(config, instructorAccess.listInstructors(config.getSubjectSection().getId()));
    }
}
