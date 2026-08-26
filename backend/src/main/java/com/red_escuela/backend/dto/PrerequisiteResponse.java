package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.entity.RequirementType;

public record PrerequisiteResponse(
        Long id, Long prerequisiteProgramSubjectId, String prerequisiteSubjectName,
        RequirementType requirementType, Double minGrade, String notes) {

    public static PrerequisiteResponse from(ProgramSubjectPrerequisite prerequisite) {
        return new PrerequisiteResponse(
                prerequisite.getId(),
                prerequisite.getPrerequisiteProgramSubject().getId(),
                prerequisite.getPrerequisiteProgramSubject().getSubject().getName(),
                prerequisite.getRequirementType(),
                prerequisite.getMinGrade(),
                prerequisite.getNotes());
    }
}
