package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record PrerequisiteView(Long prerequisiteSubjectId, String prerequisiteSubjectName,
                               RequirementType requirementType) {
}
