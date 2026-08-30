package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.SectionEvaluationConfig;
import com.red_escuela.backend.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.service.SectionEvaluationConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeSectionEvaluationConfigController {

    private final SectionEvaluationConfigService sectionEvaluationConfigService;
    private final CurrentInstructorService currentInstructorService;

    public MeSectionEvaluationConfigController(
            SectionEvaluationConfigService sectionEvaluationConfigService,
            CurrentInstructorService currentInstructorService) {
        this.sectionEvaluationConfigService = sectionEvaluationConfigService;
        this.currentInstructorService = currentInstructorService;
    }

    @GetMapping("/me/section-evaluation-configs")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<SectionEvaluationConfigResponse> mySections() {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return sectionEvaluationConfigService.listByInstructor(instructor.getId()).stream()
                .map(config -> toResponse(config, instructor.getId())).toList();
    }

    @GetMapping("/me/section-evaluation-configs/{sectionId}")
    @PreAuthorize("hasRole('DOCENTE')")
    public SectionEvaluationConfigResponse get(@PathVariable Long sectionId) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return toResponse(sectionEvaluationConfigService.getBySection(sectionId, instructor.getId()), instructor.getId());
    }

    @PutMapping("/me/section-evaluation-configs/{sectionId}")
    @PreAuthorize("hasRole('DOCENTE')")
    public SectionEvaluationConfigResponse saveDraft(
            @PathVariable Long sectionId, @Valid @RequestBody UpdateSectionEvaluationConfigRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return toResponse(sectionEvaluationConfigService.saveDraft(sectionId, instructor.getId(), request), instructor.getId());
    }

    @PostMapping("/me/section-evaluation-configs/{sectionId}/submit")
    @PreAuthorize("hasRole('DOCENTE')")
    public SectionEvaluationConfigResponse submitForApproval(@PathVariable Long sectionId) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return toResponse(sectionEvaluationConfigService.submitForApproval(sectionId, instructor.getId()), instructor.getId());
    }

    private SectionEvaluationConfigResponse toResponse(SectionEvaluationConfig config, Long instructorId) {
        Long sectionId = config.getSubjectSection().getId();
        boolean isLead = sectionEvaluationConfigService.isLeadInstructor(sectionId, instructorId);
        boolean isEditableStatus = config.getStatus() == SectionEvaluationConfigStatus.DRAFT
                || config.getStatus() == SectionEvaluationConfigStatus.REJECTED;
        return SectionEvaluationConfigResponse.from(
                config, sectionEvaluationConfigService.listInstructors(sectionId), isLead && isEditableStatus);
    }
}
