package com.red_escuela.backend.evaluation.usecase.gradegrid;

import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;

public interface GetTeacherProgramSectionGradeGridUseCase {
    TeacherGradeGridResponse get(GetSectionGradeGridCommand command);
}
