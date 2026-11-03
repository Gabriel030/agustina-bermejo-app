package com.red_escuela.backend.evaluation.usecase.gradegrid;

import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.service.TeacherGradeGridBuilder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetTeacherProgramSectionGradeGridService implements GetTeacherProgramSectionGradeGridUseCase {

    private final TeacherGradeGridBuilder gridBuilder;

    public GetTeacherProgramSectionGradeGridService(TeacherGradeGridBuilder gridBuilder) {
        this.gridBuilder = gridBuilder;
    }

    @Override
    public TeacherGradeGridResponse get(GetSectionGradeGridCommand command) {
        return gridBuilder.buildForProgramSection(command.sectionId(), command.instructorId());
    }
}
