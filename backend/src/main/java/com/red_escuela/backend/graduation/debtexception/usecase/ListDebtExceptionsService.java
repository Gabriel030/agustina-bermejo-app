package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionLister;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListDebtExceptionsService implements ListDebtExceptionsUseCase {

    private final DebtExceptionLister lister;
    public ListDebtExceptionsService(DebtExceptionLister lister) {
        this.lister = lister;
    }

    @Override
    public Page<DebtExceptionResponse> list(DebtExceptionStatus status, Long studentId, Pageable pageable) {
        return lister.list(status, studentId, pageable);
    }
}
