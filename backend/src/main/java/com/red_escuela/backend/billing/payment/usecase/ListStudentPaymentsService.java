package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.service.StudentPaymentQueryService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@UseCase
public class ListStudentPaymentsService implements ListStudentPaymentsUseCase {
    private final StudentPaymentQueryService queryService;

    public ListStudentPaymentsService(StudentPaymentQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<Payment> listPaymentsOfStudent(Long studentId) {
        return queryService.findPaymentsOfExistingStudent(studentId);
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public List<Payment> listPaymentsOfCurrentStudent(Long currentStudentId) {
        return queryService.findPaymentsOfStudent(currentStudentId);
    }
}
