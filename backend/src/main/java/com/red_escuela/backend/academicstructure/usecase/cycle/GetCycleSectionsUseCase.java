package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;

import java.util.List;

public interface GetCycleSectionsUseCase {
    List<CycleSectionResponse> getSections(Long cycleId);
}
