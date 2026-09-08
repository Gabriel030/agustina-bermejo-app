package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.service.AdHocInvoiceRequestResolver;
import com.red_escuela.backend.billing.service.InvoiceFactory;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateAdHocInvoiceForProgramEnrollmentService implements CreateAdHocInvoiceForProgramEnrollmentUseCase {

    private final InvoiceFactory invoiceFactory;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final AdHocInvoiceRequestResolver requestResolver;

    public CreateAdHocInvoiceForProgramEnrollmentService(InvoiceFactory invoiceFactory, ProgramEnrollmentRepository programEnrollmentRepository,
                                                           AdHocInvoiceRequestResolver requestResolver) {
        this.invoiceFactory = invoiceFactory;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.requestResolver = requestResolver;
    }

    @Override
    public Invoice create(Long programEnrollmentId, CreateAdHocInvoiceRequest request) {
        requestResolver.validateFeeType(request.feeType());
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        Subject subject = requestResolver.resolveSubject(request.subjectId());
        ExamBoardRegistration examBoardRegistration = requestResolver.resolveExamBoardRegistration(request.examBoardRegistrationId());
        return invoiceFactory.createAdHocFeeForProgramEnrollment(
                enrollment, request.feeType(), request.amount(), request.dueDate(), request.description(), subject, examBoardRegistration);
    }
}
