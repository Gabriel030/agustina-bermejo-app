package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Refund;

import java.util.List;

public interface ListStudentRefundsUseCase {
    List<Refund> listRefundsOfStudent(Long studentId);

    List<Refund> listRefundsOfCurrentStudent(Long currentStudentId);
}
