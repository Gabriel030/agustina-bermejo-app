package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.GetSectionEvaluationConfigForStaffUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ListSectionEvaluationConfigsUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.RejectSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.RejectSectionEvaluationConfigUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
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

    public SectionEvaluationConfigController(
            ListSectionEvaluationConfigsUseCase listSectionEvaluationConfigsUseCase,
            GetSectionEvaluationConfigForStaffUseCase getSectionEvaluationConfigForStaffUseCase,
            ApproveSectionEvaluationConfigUseCase approveSectionEvaluationConfigUseCase,
            RejectSectionEvaluationConfigUseCase rejectSectionEvaluationConfigUseCase) {
        this.listSectionEvaluationConfigsUseCase = listSectionEvaluationConfigsUseCase;
        this.getSectionEvaluationConfigForStaffUseCase = getSectionEvaluationConfigForStaffUseCase;
        this.approveSectionEvaluationConfigUseCase = approveSectionEvaluationConfigUseCase;
        this.rejectSectionEvaluationConfigUseCase = rejectSectionEvaluationConfigUseCase;
    }

    @GetMapping("/section-evaluation-configs")
    @RequiresPermission(code = "evaluation.read_evaluation_settings", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public List<SectionEvaluationConfigResponse> list(@RequestParam(required = false) SectionEvaluationConfigStatus status) {
        return listSectionEvaluationConfigsUseCase.list(status);
    }

    @GetMapping("/section-evaluation-configs/{sectionId}")
    @RequiresPermission(code = "evaluation.read_evaluation_settings", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public SectionEvaluationConfigResponse get(@PathVariable Long sectionId) {
        return getSectionEvaluationConfigForStaffUseCase.get(sectionId);
    }

    @PostMapping("/section-evaluation-configs/{sectionId}/approve")
    @RequiresPermission(code = "evaluation.review_section_evaluation_config", legacyRoles = "ROLE_DIRECTOR")
    public SectionEvaluationConfigResponse approve(@PathVariable Long sectionId) {
        return approveSectionEvaluationConfigUseCase.approve(new ApproveSectionEvaluationConfigCommand(sectionId));
    }

    @PostMapping("/section-evaluation-configs/{sectionId}/reject")
    @RequiresPermission(code = "evaluation.review_section_evaluation_config", legacyRoles = "ROLE_DIRECTOR")
    public SectionEvaluationConfigResponse reject(
            @PathVariable Long sectionId, @Valid @RequestBody RejectSectionEvaluationConfigRequest request) {
        return rejectSectionEvaluationConfigUseCase.reject(new RejectSectionEvaluationConfigCommand(sectionId, request));
    }
}
