package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;

public record RecordClosureObservationCommand(ClosureSectionKind kind, Long sectionId, Long enrollmentId, Long instructorId, String observation) {
}
