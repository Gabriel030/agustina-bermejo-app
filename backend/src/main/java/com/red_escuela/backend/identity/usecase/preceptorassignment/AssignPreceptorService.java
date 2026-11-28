package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.service.AssignPreceptorTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class AssignPreceptorService implements AssignPreceptorUseCase {

    private final AssignPreceptorTransaction assignPreceptorTransaction;

    public AssignPreceptorService(AssignPreceptorTransaction assignPreceptorTransaction) {
        this.assignPreceptorTransaction = assignPreceptorTransaction;
    }

    @Override
    public PreceptorAssignmentResult assign(AssignPreceptorCommand command) {
        return assignPreceptorTransaction.assign(command);
    }
}
