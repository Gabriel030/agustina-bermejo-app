package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.specialist.CommissionClosureSheetReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetClosureSheetService implements GetClosureSheetUseCase {

    private final CommissionClosureSheetReader sheetReader;

    public GetClosureSheetService(CommissionClosureSheetReader sheetReader) {
        this.sheetReader = sheetReader;
    }

    @Override
    public ClosureSheetResponse get(GetClosureSheetCommand command) {
        return sheetReader.readSheet(command.kind(), command.sectionId(), command.viewer(), command.instructorId());
    }
}
