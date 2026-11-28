package com.red_escuela.backend.identity.usecase.preceptorassignment;

import java.util.List;

public interface ListPreceptorAssignmentsUseCase {
    List<PreceptorAssignmentResult> list(ListPreceptorAssignmentsCommand command);
}
