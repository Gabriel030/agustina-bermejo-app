package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;

public interface CreateCycleSectionUseCase {
    Long createSection(Long cycleId, CreateCycleSectionRequest request);
}
