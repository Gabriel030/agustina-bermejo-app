package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.IssueDebtExceptionRequest;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class IssueDebtExceptionService implements IssueDebtExceptionUseCase {

    @Override
    public DebtExceptionResponse issue(IssueDebtExceptionRequest request) {
        throw new DocumentOperationPendingException();
    }
}
