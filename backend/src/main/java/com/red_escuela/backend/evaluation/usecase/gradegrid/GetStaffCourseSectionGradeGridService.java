package com.red_escuela.backend.evaluation.usecase.gradegrid;

import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.service.TeacherGradeGridBuilder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStaffCourseSectionGradeGridService implements GetStaffCourseSectionGradeGridUseCase {

    private final TeacherGradeGridBuilder gridBuilder;

    public GetStaffCourseSectionGradeGridService(TeacherGradeGridBuilder gridBuilder) {
        this.gridBuilder = gridBuilder;
    }

    @Override
    public TeacherGradeGridResponse get(Long sectionId) {
        return gridBuilder.buildForCourseSectionAsStaff(sectionId);
    }
}
