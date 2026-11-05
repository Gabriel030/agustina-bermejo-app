package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.StudentPaymentOverview;
import com.red_escuela.backend.billing.payment.service.StudentPaymentOverviewService;
import com.red_escuela.backend.billing.payment.service.StudentPaymentQueryService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@UseCase
public class ListStudentPaymentsService implements ListStudentPaymentsUseCase {
    private final StudentPaymentQueryService queryService;
    private final StudentPaymentOverviewService overviewService;

    public ListStudentPaymentsService(StudentPaymentQueryService queryService, StudentPaymentOverviewService overviewService) {
        this.queryService = queryService;
        this.overviewService = overviewService;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<StudentPaymentOverview> describePaymentsOfStudent(Long studentId) {
        return overviewService.describePaymentsOfExistingStudent(studentId);
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public List<Payment> listPaymentsOfCurrentStudent(Long currentStudentId) {
        return queryService.findPaymentsOfStudent(currentStudentId);
    }
}
