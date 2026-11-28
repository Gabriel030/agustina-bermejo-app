package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.service.ListPreceptorAssignmentsTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPreceptorAssignmentsService implements ListPreceptorAssignmentsUseCase {

    private final ListPreceptorAssignmentsTransaction listPreceptorAssignmentsTransaction;

    public ListPreceptorAssignmentsService(ListPreceptorAssignmentsTransaction listPreceptorAssignmentsTransaction) {
        this.listPreceptorAssignmentsTransaction = listPreceptorAssignmentsTransaction;
    }

    @Override
    public List<PreceptorAssignmentResult> list(ListPreceptorAssignmentsCommand command) {
        return listPreceptorAssignmentsTransaction.list(command);
    }
}
