package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionLister;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionAccessGuard;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ListDebtExceptionsService implements ListDebtExceptionsUseCase {

    private final DebtExceptionLister lister;
    private final DebtExceptionAccessGuard accessGuard;

    public ListDebtExceptionsService(DebtExceptionLister lister, DebtExceptionAccessGuard accessGuard) {
        this.lister = lister;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional
    public Page<DebtExceptionResponse> list(DebtExceptionStatus status, Long studentId, Pageable pageable) {
        accessGuard.requireNotSubdirector();
        return lister.list(status, studentId, pageable);
    }
}
