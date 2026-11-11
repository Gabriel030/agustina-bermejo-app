package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;

public record GrantClosureExceptionCommand(ClosureSectionKind kind, Long sectionId, Long enrollmentId, String reason) {
}
