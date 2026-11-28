package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.dto.PreceptorAssignmentResult;
import com.red_escuela.backend.identity.dto.ReplaceSectionPreceptorsCommand;

import java.util.List;

public interface ReplaceSectionPreceptorsUseCase {
    List<PreceptorAssignmentResult> replace(ReplaceSectionPreceptorsCommand command);
}
