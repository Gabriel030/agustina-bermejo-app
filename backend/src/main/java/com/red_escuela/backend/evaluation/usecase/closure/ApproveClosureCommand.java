package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;

public record ApproveClosureCommand(ClosureSectionKind kind, Long sectionId, String book, String folio, boolean closeWithoutTeacherConfirmation) {
}
