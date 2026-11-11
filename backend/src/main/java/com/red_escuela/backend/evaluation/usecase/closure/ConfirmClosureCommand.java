package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;

public record ConfirmClosureCommand(ClosureSectionKind kind, Long sectionId, Long instructorId) {
}
