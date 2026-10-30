package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateProgramSubjectRequest(
        @NotNull Long subjectId,
        @NotNull ProgramSubjectCategory category,
        Integer curricularYear,
        CurricularTerm curricularTerm,
        @NotNull @Min(0) Integer credits,
        SubjectRecurrence subjectRecurrence,
        Integer sortOrder,
        Long electiveGroupId) {
}
