package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListDebtExceptionsService implements ListDebtExceptionsUseCase {

    @Override
    public Page<DebtExceptionResponse> list(DebtExceptionStatus status, Long studentId, Pageable pageable) {
        throw new DocumentOperationPendingException();
    }
}
