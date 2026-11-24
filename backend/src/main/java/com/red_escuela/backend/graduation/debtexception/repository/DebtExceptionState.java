package com.red_escuela.backend.graduation.debtexception.repository;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import java.time.LocalDateTime;

public interface DebtExceptionState {

    DebtExceptionStatus getStatus();

    LocalDateTime getExpiresAt();
}
