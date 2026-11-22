package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListApprovedProceduresService implements ListApprovedProceduresUseCase {

    @Override
    public List<ApprovedProcedureResponse> listApproved() {
        throw new DocumentOperationPendingException();
    }
}
