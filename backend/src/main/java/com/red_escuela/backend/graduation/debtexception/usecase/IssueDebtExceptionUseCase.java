package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.IssueDebtExceptionRequest;

public interface IssueDebtExceptionUseCase {

    DebtExceptionResponse issue(IssueDebtExceptionRequest request);
}
