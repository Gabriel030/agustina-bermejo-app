package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.SectionModificationPreviewResponse;
import com.red_escuela.backend.academicstructure.usecase.cycle.PreviewSectionModificationUseCase;
import jakarta.validation.Valid;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SectionModificationPreviewController {

    private final PreviewSectionModificationUseCase previewModification;

    public SectionModificationPreviewController(PreviewSectionModificationUseCase previewModification) {
        this.previewModification = previewModification;
    }

    @PostMapping("/subject-sections/{id}/modification-request/preview")
    @RequiresPermission(code = "academicstructure.request_section_modification", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public SectionModificationPreviewResponse previewSectionModification(@PathVariable Long id,
                                                                         @Valid @RequestBody ModifySectionRequest request) {
        return previewModification.preview(id, request);
    }
}
