package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.SectionModificationPreviewResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class PreviewSectionModificationService implements PreviewSectionModificationUseCase {

    @Override
    public SectionModificationPreviewResponse preview(Long sectionId, ModifySectionRequest request) {
        return new SectionModificationPreviewResponse(List.of());
    }
}
