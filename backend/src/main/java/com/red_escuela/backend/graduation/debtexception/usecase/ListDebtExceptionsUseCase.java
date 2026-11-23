package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListDebtExceptionsUseCase {

    Page<DebtExceptionResponse> list(DebtExceptionStatus status, Long studentId, Pageable pageable);
}
