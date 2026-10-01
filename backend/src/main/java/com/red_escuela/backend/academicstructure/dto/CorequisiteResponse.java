package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;

public record CorequisiteResponse(
        Long id,
        Long corequisiteProgramSubjectId,
        String corequisiteSubjectCode,
        String corequisiteSubjectName
) {
    public static CorequisiteResponse from(ProgramSubjectCorequisite c) {
        return new CorequisiteResponse(
                c.getId(),
                c.getCorequisiteProgramSubject().getId(),
                c.getCorequisiteProgramSubject().getSubject().getCode(),
                c.getCorequisiteProgramSubject().getSubject().getName()
        );
    }
}
