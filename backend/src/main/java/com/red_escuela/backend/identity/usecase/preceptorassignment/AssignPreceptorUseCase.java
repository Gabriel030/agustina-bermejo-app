package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.dto.AssignPreceptorCommand;
import com.red_escuela.backend.identity.dto.PreceptorAssignmentResult;

public interface AssignPreceptorUseCase {
    PreceptorAssignmentResult assign(AssignPreceptorCommand command);
}
