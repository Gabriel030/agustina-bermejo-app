package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.IssueDebtExceptionRequest;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionIssuer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class IssueDebtExceptionService implements IssueDebtExceptionUseCase {

    private final DebtExceptionIssuer issuer;

    public IssueDebtExceptionService(DebtExceptionIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    public DebtExceptionResponse issue(IssueDebtExceptionRequest request) {
        return issuer.issue(request);
    }
}
