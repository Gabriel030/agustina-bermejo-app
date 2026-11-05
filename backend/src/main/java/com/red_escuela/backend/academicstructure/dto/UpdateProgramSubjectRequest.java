package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;

public record UpdateProgramSubjectRequest(
        Integer curricularYear, CurricularTerm curricularTerm, Integer credits,
        SubjectRecurrence subjectRecurrence, Integer sortOrder, Long electiveGroupId) implements SelfValidatingRequest {
    public UpdateProgramSubjectRequest {
        RequestLimits.requireAtMost("credits", credits, 1000);
        RequestLimits.requireAtMost("curricularYear", curricularYear, 20);
        RequestLimits.requireAtMost("sortOrder", sortOrder, 10000);
    }
}
