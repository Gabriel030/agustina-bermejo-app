package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.StudentCreditBalance;
import com.red_escuela.backend.shared.domain.Currency;

public interface DescribeStudentCreditBalanceUseCase {
    StudentCreditBalance describeOwnCreditBalance(Long currentStudentId, Currency currency);

    StudentCreditBalance describeCreditBalanceOfStudent(Long studentId, Currency currency);
}
