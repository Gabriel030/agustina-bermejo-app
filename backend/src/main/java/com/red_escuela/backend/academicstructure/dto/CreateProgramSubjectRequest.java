package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer curricularYear,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) CurricularTerm curricularTerm,
        @NotNull @Min(0) Integer credits,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) SubjectRecurrence subjectRecurrence,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer sortOrder,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long electiveGroupId) implements SelfValidatingRequest {
    public CreateProgramSubjectRequest {
        RequestLimits.requireAtMost("credits", credits, 1000);
        RequestLimits.requireAtMost("curricularYear", curricularYear, 20);
        RequestLimits.requireAtMost("sortOrder", sortOrder, 10000);
    }
}
