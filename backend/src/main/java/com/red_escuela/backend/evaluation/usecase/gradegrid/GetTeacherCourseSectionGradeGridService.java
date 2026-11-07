package com.red_escuela.backend.evaluation.usecase.gradegrid;

import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.specialist.TeacherGradeGridBuilder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetTeacherCourseSectionGradeGridService implements GetTeacherCourseSectionGradeGridUseCase {

    private final TeacherGradeGridBuilder gridBuilder;

    public GetTeacherCourseSectionGradeGridService(TeacherGradeGridBuilder gridBuilder) {
        this.gridBuilder = gridBuilder;
    }

    @Override
    public TeacherGradeGridResponse get(GetSectionGradeGridCommand command) {
        return gridBuilder.buildForCourseSection(command.sectionId(), command.instructorId());
    }
}
