package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.ClosureExportFile;

public interface ExportClosureUseCase {

    ClosureExportFile export(ExportClosureCommand command);
}
