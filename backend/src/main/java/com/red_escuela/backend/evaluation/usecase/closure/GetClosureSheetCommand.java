package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;

public record GetClosureSheetCommand(ClosureSectionKind kind, Long sectionId, ClosureViewerProfile viewer, Long instructorId) {
}
