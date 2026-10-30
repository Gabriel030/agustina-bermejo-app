package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;

public record UpdateProgramSubjectRequest(
        Integer curricularYear, CurricularTerm curricularTerm, Integer credits,
        SubjectRecurrence subjectRecurrence, Integer sortOrder, Long electiveGroupId) {
}
