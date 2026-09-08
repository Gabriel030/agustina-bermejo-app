package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.SectionEvaluationConfig;
import com.red_escuela.backend.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.service.SectionEvaluationConfigService;
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

    private final SectionEvaluationConfigService sectionEvaluationConfigService;
    private final CurrentPersonService currentPersonService;

    public SectionEvaluationConfigController(
            SectionEvaluationConfigService sectionEvaluationConfigService, CurrentPersonService currentPersonService) {
        this.sectionEvaluationConfigService = sectionEvaluationConfigService;
        this.currentPersonService = currentPersonService;
    }

    @GetMapping("/section-evaluation-configs")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<SectionEvaluationConfigResponse> list(@RequestParam(required = false) SectionEvaluationConfigStatus status) {
        List<SectionEvaluationConfig> configs = status == null
                ? sectionEvaluationConfigService.listByStatus(SectionEvaluationConfigStatus.PENDING_APPROVAL)
                : sectionEvaluationConfigService.listByStatus(status);
        return configs.stream().map(this::toResponse).toList();
    }

    @GetMapping("/section-evaluation-configs/{sectionId}")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEvaluationConfigResponse get(@PathVariable Long sectionId) {
        return toResponse(sectionEvaluationConfigService.getBySectionForStaff(sectionId));
    }

    @PostMapping("/section-evaluation-configs/{sectionId}/approve")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEvaluationConfigResponse approve(@PathVariable Long sectionId) {
        Person actor = currentPersonService.resolveCurrentPerson();
        return toResponse(sectionEvaluationConfigService.approve(sectionId, actor));
    }

    @PostMapping("/section-evaluation-configs/{sectionId}/reject")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public SectionEvaluationConfigResponse reject(
            @PathVariable Long sectionId, @Valid @RequestBody RejectSectionEvaluationConfigRequest request) {
        Person actor = currentPersonService.resolveCurrentPerson();
        return toResponse(sectionEvaluationConfigService.reject(sectionId, actor, request));
    }

    private SectionEvaluationConfigResponse toResponse(SectionEvaluationConfig config) {
        return SectionEvaluationConfigResponse.from(config, sectionEvaluationConfigService.listInstructors(config.getSubjectSection().getId()));
    }
}
