package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.dto.ListPreceptorAssignmentsCommand;
import com.red_escuela.backend.identity.dto.PreceptorAssignmentResult;

import java.util.List;

public interface ListPreceptorAssignmentsUseCase {
    List<PreceptorAssignmentResult> list(ListPreceptorAssignmentsCommand command);
}
