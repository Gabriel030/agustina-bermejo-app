package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.StudentPaymentOverview;

import java.util.List;

public interface ListStudentPaymentsUseCase {
    List<StudentPaymentOverview> describePaymentsOfStudent(Long studentId);

    List<Payment> listPaymentsOfCurrentStudent(Long currentStudentId);
}
