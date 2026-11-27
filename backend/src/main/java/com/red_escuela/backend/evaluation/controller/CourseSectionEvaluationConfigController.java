package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.GetSectionEvaluationConfigForStaffUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ListCourseSectionEvaluationConfigsUseCase;
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
public class CourseSectionEvaluationConfigController {

    private final ListCourseSectionEvaluationConfigsUseCase listCourseSectionEvaluationConfigsUseCase;
    private final GetSectionEvaluationConfigForStaffUseCase getSectionEvaluationConfigForStaffUseCase;
    private final ApproveSectionEvaluationConfigUseCase approveSectionEvaluationConfigUseCase;
    private final RejectSectionEvaluationConfigUseCase rejectSectionEvaluationConfigUseCase;

    public CourseSectionEvaluationConfigController(
            ListCourseSectionEvaluationConfigsUseCase listCourseSectionEvaluationConfigsUseCase,
            GetSectionEvaluationConfigForStaffUseCase getSectionEvaluationConfigForStaffUseCase,
            ApproveSectionEvaluationConfigUseCase approveSectionEvaluationConfigUseCase,
            RejectSectionEvaluationConfigUseCase rejectSectionEvaluationConfigUseCase) {
        this.listCourseSectionEvaluationConfigsUseCase = listCourseSectionEvaluationConfigsUseCase;
        this.getSectionEvaluationConfigForStaffUseCase = getSectionEvaluationConfigForStaffUseCase;
        this.approveSectionEvaluationConfigUseCase = approveSectionEvaluationConfigUseCase;
        this.rejectSectionEvaluationConfigUseCase = rejectSectionEvaluationConfigUseCase;
    }

    @GetMapping("/course-section-evaluation-configs")
    @RequiresPermission(code = "evaluation.read_evaluation_settings", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public List<SectionEvaluationConfigResponse> list(@RequestParam(required = false) SectionEvaluationConfigStatus status) {
        return listCourseSectionEvaluationConfigsUseCase.list(status == null ? null : status.name());
    }

    @GetMapping("/course-section-evaluation-configs/{courseSectionId}")
    @RequiresPermission(code = "evaluation.read_evaluation_settings", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public SectionEvaluationConfigResponse get(@PathVariable Long courseSectionId) {
        return getSectionEvaluationConfigForStaffUseCase.get(SectionRef.course(courseSectionId));
    }

    @PostMapping("/course-section-evaluation-configs/{courseSectionId}/approve")
    @RequiresPermission(code = "evaluation.review_section_evaluation_config", legacyRoles = "ROLE_DIRECTOR")
    public SectionEvaluationConfigResponse approve(@PathVariable Long courseSectionId) {
        return approveSectionEvaluationConfigUseCase.approve(
                new ApproveSectionEvaluationConfigCommand(SectionRef.course(courseSectionId)));
    }

    @PostMapping("/course-section-evaluation-configs/{courseSectionId}/reject")
    @RequiresPermission(code = "evaluation.review_section_evaluation_config", legacyRoles = "ROLE_DIRECTOR")
    public SectionEvaluationConfigResponse reject(
            @PathVariable Long courseSectionId, @Valid @RequestBody RejectSectionEvaluationConfigRequest request) {
        return rejectSectionEvaluationConfigUseCase.reject(
                new RejectSectionEvaluationConfigCommand(SectionRef.course(courseSectionId), request));
    }
}
