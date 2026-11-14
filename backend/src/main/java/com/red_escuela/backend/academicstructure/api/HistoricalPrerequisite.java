package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import java.math.BigDecimal;

public record HistoricalPrerequisite(String programName, String subjectCode, String prerequisiteCode,
        RequirementType requirementType, BigDecimal minGrade) {
}
