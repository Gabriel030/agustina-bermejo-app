package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundRequestGroup;
import com.red_escuela.backend.billing.payment.service.StudentRefundQueryService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@UseCase
public class ListStudentRefundsService implements ListStudentRefundsUseCase {
    private final StudentRefundQueryService queryService;

    public ListStudentRefundsService(StudentRefundQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<Refund> listRefundsOfStudent(Long studentId) {
        return queryService.findRefundsOfExistingStudent(studentId);
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public List<Refund> listRefundsOfCurrentStudent(Long currentStudentId) {
        return queryService.findRefundsOfStudent(currentStudentId);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<RefundRequestGroup> listRefundRequestsOfStudent(Long studentId) {
        return queryService.findRefundRequestsOfExistingStudent(studentId);
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public List<RefundRequestGroup> listRefundRequestsOfCurrentStudent(Long currentStudentId) {
        return queryService.findRefundRequestsOfStudent(currentStudentId);
    }
}
