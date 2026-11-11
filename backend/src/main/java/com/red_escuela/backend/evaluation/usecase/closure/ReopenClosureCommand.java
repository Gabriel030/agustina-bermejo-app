package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;

public record ReopenClosureCommand(ClosureSectionKind kind, Long sectionId, String reason) {
}
