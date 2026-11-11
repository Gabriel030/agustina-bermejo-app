package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.evaluation.domain.ClosurePrerequisiteStatus;

public record ClosurePrerequisiteResponse(
        String subjectCode,
        String subjectName,
        RequirementType requirementType,
        ClosurePrerequisiteStatus studentStatus,
        boolean met) {
}
