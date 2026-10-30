package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.shared.domain.Grade;
import java.math.BigDecimal;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;

public record PrerequisiteResponse(
        Long id, Long prerequisiteProgramSubjectId, String prerequisiteSubjectCode, String prerequisiteSubjectName,
        RequirementType requirementType, BigDecimal minGrade, String notes) {

    public static PrerequisiteResponse from(ProgramSubjectPrerequisite prerequisite) {
        return new PrerequisiteResponse(
                prerequisite.getId(),
                prerequisite.getPrerequisiteProgramSubject().getId(),
                prerequisite.getPrerequisiteProgramSubject().getSubject().getCode(),
                prerequisite.getPrerequisiteProgramSubject().getSubject().getName(),
                prerequisite.getRequirementType(),
                Grade.valueOrNull(prerequisite.getMinGrade()),
                prerequisite.getNotes());
    }
}
