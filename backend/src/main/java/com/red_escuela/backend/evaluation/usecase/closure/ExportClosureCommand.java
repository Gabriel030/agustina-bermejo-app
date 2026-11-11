package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.ClosureExportFormat;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;

public record ExportClosureCommand(Long closureId, ClosureExportFormat format, ClosureViewerProfile viewer, Long instructorId) {
}
