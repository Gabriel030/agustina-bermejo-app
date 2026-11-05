package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundRequestGroup;

import java.util.List;

public interface ListStudentRefundsUseCase {
    List<Refund> listRefundsOfStudent(Long studentId);

    List<Refund> listRefundsOfCurrentStudent(Long currentStudentId);

    List<RefundRequestGroup> listRefundRequestsOfStudent(Long studentId);

    List<RefundRequestGroup> listRefundRequestsOfCurrentStudent(Long currentStudentId);
}
