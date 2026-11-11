package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;

public record GetClosureStudentPrerequisitesCommand(Long closureId, Long enrollmentId, ClosureViewerProfile viewer, Long instructorId) {
}
