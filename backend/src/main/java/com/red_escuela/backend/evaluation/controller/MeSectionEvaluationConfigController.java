package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.GetMySectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.GetMySectionEvaluationConfigUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.ListMySectionEvaluationConfigsUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.SaveSectionEvaluationConfigDraftCommand;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.SaveSectionEvaluationConfigDraftUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.SubmitSectionEvaluationConfigForApprovalCommand;
import com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig.SubmitSectionEvaluationConfigForApprovalUseCase;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class MeSectionEvaluationConfigController {

    private final ListMySectionEvaluationConfigsUseCase listMySectionEvaluationConfigsUseCase;
    private final GetMySectionEvaluationConfigUseCase getMySectionEvaluationConfigUseCase;
    private final SaveSectionEvaluationConfigDraftUseCase saveSectionEvaluationConfigDraftUseCase;
    private final SubmitSectionEvaluationConfigForApprovalUseCase submitSectionEvaluationConfigForApprovalUseCase;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final CurrentInstructorService currentInstructorService;

    public MeSectionEvaluationConfigController(
            ListMySectionEvaluationConfigsUseCase listMySectionEvaluationConfigsUseCase,
            GetMySectionEvaluationConfigUseCase getMySectionEvaluationConfigUseCase,
            SaveSectionEvaluationConfigDraftUseCase saveSectionEvaluationConfigDraftUseCase,
            SubmitSectionEvaluationConfigForApprovalUseCase submitSectionEvaluationConfigForApprovalUseCase,
            SectionEvaluationConfigInstructorAccess instructorAccess,
            CurrentInstructorService currentInstructorService) {
        this.listMySectionEvaluationConfigsUseCase = listMySectionEvaluationConfigsUseCase;
        this.getMySectionEvaluationConfigUseCase = getMySectionEvaluationConfigUseCase;
        this.saveSectionEvaluationConfigDraftUseCase = saveSectionEvaluationConfigDraftUseCase;
        this.submitSectionEvaluationConfigForApprovalUseCase = submitSectionEvaluationConfigForApprovalUseCase;
        this.instructorAccess = instructorAccess;
        this.currentInstructorService = currentInstructorService;
    }

    @GetMapping("/me/section-evaluation-configs")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<SectionEvaluationConfigResponse> mySections() {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return listMySectionEvaluationConfigsUseCase.list(instructor.getId()).stream()
                .map(config -> toResponse(config, instructor.getId())).toList();
    }

    @GetMapping("/me/section-evaluation-configs/{sectionId}")
    @PreAuthorize("hasRole('DOCENTE')")
    public SectionEvaluationConfigResponse get(@PathVariable Long sectionId) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return toResponse(getMySectionEvaluationConfigUseCase.get(
                new GetMySectionEvaluationConfigCommand(sectionId, instructor.getId())), instructor.getId());
    }

    @PutMapping("/me/section-evaluation-configs/{sectionId}")
    @PreAuthorize("hasRole('DOCENTE')")
    public SectionEvaluationConfigResponse saveDraft(
            @PathVariable Long sectionId, @Valid @RequestBody UpdateSectionEvaluationConfigRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return toResponse(saveSectionEvaluationConfigDraftUseCase.save(
                new SaveSectionEvaluationConfigDraftCommand(sectionId, instructor.getId(), request)), instructor.getId());
    }

    @PostMapping("/me/section-evaluation-configs/{sectionId}/submit")
    @PreAuthorize("hasRole('DOCENTE')")
    public SectionEvaluationConfigResponse submitForApproval(@PathVariable Long sectionId) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return toResponse(submitSectionEvaluationConfigForApprovalUseCase.submit(
                new SubmitSectionEvaluationConfigForApprovalCommand(sectionId, instructor.getId())), instructor.getId());
    }

    private SectionEvaluationConfigResponse toResponse(SectionEvaluationConfig config, Long instructorId) {
        Long sectionId = config.getSubjectSection().getId();
        boolean isLead = instructorAccess.isLeadInstructor(sectionId, instructorId);
        return SectionEvaluationConfigResponse.from(
                config, instructorAccess.listInstructors(sectionId),
                isLead && config.isEditable(), isLead && config.canBeSubmitted(LocalDateTime.now()));
    }
}
