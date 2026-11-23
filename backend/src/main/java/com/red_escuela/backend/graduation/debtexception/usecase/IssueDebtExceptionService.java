package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.IssueDebtExceptionRequest;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionIssuer;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionAccessGuard;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class IssueDebtExceptionService implements IssueDebtExceptionUseCase {

    private final DebtExceptionIssuer issuer;
    private final DebtExceptionAccessGuard accessGuard;

    public IssueDebtExceptionService(DebtExceptionIssuer issuer, DebtExceptionAccessGuard accessGuard) {
        this.issuer = issuer;
        this.accessGuard = accessGuard;
    }

    @Override
    public DebtExceptionResponse issue(IssueDebtExceptionRequest request) {
        accessGuard.requireNotSubdirector();
        return issuer.issue(request);
    }
}
