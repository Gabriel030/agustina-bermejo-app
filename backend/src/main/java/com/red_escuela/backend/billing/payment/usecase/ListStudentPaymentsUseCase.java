package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Payment;

import java.util.List;

public interface ListStudentPaymentsUseCase {
    List<Payment> listPaymentsOfStudent(Long studentId);

    List<Payment> listPaymentsOfCurrentStudent(Long currentStudentId);
}
