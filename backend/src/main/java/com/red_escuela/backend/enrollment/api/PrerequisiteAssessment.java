package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.shared.domain.Grade;

public record PrerequisiteAssessment(Long subjectId, String subjectName, RequirementType requirementType,
                                     PrerequisiteStanding standing, boolean met, Grade minGrade,
                                     Grade approvedScore) {
    public String standingName() {
        return standing.name();
    }
}
