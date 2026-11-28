package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.dto.PreceptorAssignmentResult;
import com.red_escuela.backend.identity.dto.ReplaceSectionPreceptorsCommand;
import com.red_escuela.backend.identity.service.ReplaceSectionPreceptorsTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ReplaceSectionPreceptorsService implements ReplaceSectionPreceptorsUseCase {
    private final ReplaceSectionPreceptorsTransaction replaceSectionPreceptorsTransaction;
    public ReplaceSectionPreceptorsService(ReplaceSectionPreceptorsTransaction replaceSectionPreceptorsTransaction) {
        this.replaceSectionPreceptorsTransaction = replaceSectionPreceptorsTransaction;
    }
    @Override
    public List<PreceptorAssignmentResult> replace(ReplaceSectionPreceptorsCommand command) {
        return replaceSectionPreceptorsTransaction.replace(command);
    }
}
