package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;

public record HistoricalPlanEntry(String programName, String subjectCode, Integer curricularYear,
        CurricularTerm curricularTerm, int credits, ProgramSubjectCategory category, SubjectRecurrence recurrence,
        Integer sortOrder) {
}
