package com.red_escuela.backend.evaluation.usecase.gradegrid;

import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.specialist.TeacherGradeGridBuilder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStaffProgramSectionGradeGridService implements GetStaffProgramSectionGradeGridUseCase {

    private final TeacherGradeGridBuilder gridBuilder;

    public GetStaffProgramSectionGradeGridService(TeacherGradeGridBuilder gridBuilder) {
        this.gridBuilder = gridBuilder;
    }

    @Override
    public TeacherGradeGridResponse get(Long sectionId) {
        return gridBuilder.buildForProgramSectionAsStaff(sectionId);
    }
}
