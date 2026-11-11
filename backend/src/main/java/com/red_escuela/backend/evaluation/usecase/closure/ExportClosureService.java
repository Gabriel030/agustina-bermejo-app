package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.ClosureExportFile;
import com.red_escuela.backend.evaluation.specialist.ClosureExporter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ExportClosureService implements ExportClosureUseCase {

    private final ClosureExporter closureExporter;

    public ExportClosureService(ClosureExporter closureExporter) {
        this.closureExporter = closureExporter;
    }

    @Override
    public ClosureExportFile export(ExportClosureCommand command) {
        return closureExporter.export(command.closureId(), command.format(), command.viewer(), command.instructorId());
    }
}
