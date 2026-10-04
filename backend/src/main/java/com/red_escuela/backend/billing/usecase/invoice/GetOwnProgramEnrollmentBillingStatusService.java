package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.service.InvoiceDetailLoader;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class GetOwnProgramEnrollmentBillingStatusService implements GetOwnProgramEnrollmentBillingStatusUseCase {
    private final ProgramEnrollmentRepository enrollmentRepository;
    private final InvoiceDetailLoader invoiceDetailLoader;

    public GetOwnProgramEnrollmentBillingStatusService(ProgramEnrollmentRepository enrollmentRepository, InvoiceDetailLoader invoiceDetailLoader) {
        this.enrollmentRepository = enrollmentRepository;
        this.invoiceDetailLoader = invoiceDetailLoader;
    }

    @Override
    @Transactional(readOnly = true)
    public MeEnrollmentBillingStatusResponse get(Long studentId, Long enrollmentId) {
        ProgramEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, enrollmentId);
        }
        return MeEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.byProgramEnrollment(enrollmentId));
    }
}
