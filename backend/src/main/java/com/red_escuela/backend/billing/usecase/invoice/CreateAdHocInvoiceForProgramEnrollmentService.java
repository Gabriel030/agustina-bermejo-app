package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.service.AdHocInvoiceRequestResolver;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingResult;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateAdHocInvoiceForProgramEnrollmentService implements CreateAdHocInvoiceForProgramEnrollmentUseCase {

    private final InvoiceIssuingService invoiceIssuingService;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final AdHocInvoiceRequestResolver requestResolver;

    public CreateAdHocInvoiceForProgramEnrollmentService(InvoiceIssuingService invoiceIssuingService, ProgramEnrollmentRepository programEnrollmentRepository,
                                                           AdHocInvoiceRequestResolver requestResolver) {
        this.invoiceIssuingService = invoiceIssuingService;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.requestResolver = requestResolver;
    }

    @Override
    @Transactional
    public Invoice create(Long programEnrollmentId, CreateAdHocInvoiceRequest request) {
        requestResolver.validateFeeType(request.feeType());
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        if (request.amount().currency() != enrollment.getProgram().getCurrency()) {
            throw new CurrencyMismatchException(enrollment.getProgram().getCurrency(), request.amount().currency());
        }
        Subject subject = requestResolver.resolveSubject(request.subjectId());
        ExamBoardRegistration examBoardRegistration = requestResolver.resolveExamBoardRegistration(request.examBoardRegistrationId());
        InvoiceIssuingRequest chargeRequest = new InvoiceIssuingRequest(new InvoiceRecipient.ProgramEnrollmentRecipient(enrollment), request.feeType(),
                java.time.YearMonth.from(request.dueDate()), request.dueDate(), InvoiceIssuingReason.AD_HOC_CHARGE, null, null, null, false, request.amount(),
                request.description(), subject, examBoardRegistration);
        return switch (invoiceIssuingService.issueInvoiceInCallerTransaction(chargeRequest)) {
            case InvoiceIssuingResult.InvoiceIssued issued -> issued.invoice();
            case InvoiceIssuingResult.InvoiceIssuingRejected rejected -> throw new ChargeRejectedException(rejected.evaluation());
            case InvoiceIssuingResult.InvoiceAlreadyIssued duplicated -> throw new com.red_escuela.backend.billing.exception.DuplicateChargeException("ad hoc");
        };
    }
}
