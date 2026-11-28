package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.service.UnassignPreceptorTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UnassignPreceptorService implements UnassignPreceptorUseCase {
    private final UnassignPreceptorTransaction unassignPreceptorTransaction;
    public UnassignPreceptorService(UnassignPreceptorTransaction unassignPreceptorTransaction) {
        this.unassignPreceptorTransaction = unassignPreceptorTransaction;
    }
    @Override
    public void unassign(Long assignmentId) {
        unassignPreceptorTransaction.unassign(assignmentId);
    }
}
