package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;

public interface GetClosureSheetUseCase {

    ClosureSheetResponse get(GetClosureSheetCommand command);
}
