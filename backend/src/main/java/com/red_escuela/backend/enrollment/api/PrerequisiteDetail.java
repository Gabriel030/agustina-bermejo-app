package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.shared.domain.Grade;

public record PrerequisiteDetail(Long subjectId, String subjectCode, String subjectName, RequirementType requirementType,
                                 PrerequisiteStanding standing, boolean metForApproval,
                                 Grade minGrade, Grade approvedScore) {
}
