package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;

import java.util.List;

public interface ListCurriculumPlansUseCase {
    List<CurriculumPlanResponse> listByProgram(Long programId);
}
