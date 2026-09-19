package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;

public interface RequestSectionModificationUseCase {
    void requestModification(Long sectionId, ModifySectionRequest request);
}
