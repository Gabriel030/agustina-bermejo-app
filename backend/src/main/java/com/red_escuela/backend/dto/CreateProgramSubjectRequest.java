package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.SubjectRecurrence;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateProgramSubjectRequest(
        @NotNull Long subjectId, @NotNull @Min(1) Integer curricularYear,
        @NotNull CurricularTerm curricularTerm, @NotNull @Min(0) Integer credits,
        SubjectRecurrence subjectRecurrence, Integer sortOrder, Long electiveGroupId) {
}
