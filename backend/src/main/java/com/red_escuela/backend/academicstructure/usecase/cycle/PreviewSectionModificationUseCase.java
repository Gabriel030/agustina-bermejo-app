package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.SectionModificationPreviewResponse;

public interface PreviewSectionModificationUseCase {
    SectionModificationPreviewResponse preview(Long sectionId, ModifySectionRequest request);
}
