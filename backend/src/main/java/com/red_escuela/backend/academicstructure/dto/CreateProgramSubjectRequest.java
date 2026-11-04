package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
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
        Long electiveGroupId) implements SelfValidatingRequest {
    public CreateProgramSubjectRequest {
        RequestLimits.requireBetween("credits", credits, 0, 1000);
        RequestLimits.requireBetween("curricularYear", curricularYear, 1, 20);
        RequestLimits.requireBetween("sortOrder", sortOrder, 0, 10000);
    }
}
