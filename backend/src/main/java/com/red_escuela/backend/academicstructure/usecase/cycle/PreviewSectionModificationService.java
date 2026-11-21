package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.SectionModificationPreviewResponse;
import com.red_escuela.backend.academicstructure.service.SectionModificationPreviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewSectionModificationService implements PreviewSectionModificationUseCase {

    private final SectionModificationPreviewer previewer;

    public PreviewSectionModificationService(SectionModificationPreviewer previewer) {
        this.previewer = previewer;
    }

    @Override
    public SectionModificationPreviewResponse preview(Long sectionId, ModifySectionRequest request) {
        return previewer.previewModification(sectionId, request);
    }
}
