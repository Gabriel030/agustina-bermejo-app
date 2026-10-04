package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.service.AdHocInvoiceRequestResolver;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.issuing.ChargeIssuer;
import com.red_escuela.backend.billing.issuing.ChargeRequest;
import com.red_escuela.backend.billing.issuing.ChargeTarget;
import com.red_escuela.backend.billing.issuing.IssueResult;
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

    private final ChargeIssuer chargeIssuer;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final AdHocInvoiceRequestResolver requestResolver;

    public CreateAdHocInvoiceForProgramEnrollmentService(ChargeIssuer chargeIssuer, ProgramEnrollmentRepository programEnrollmentRepository,
                                                           AdHocInvoiceRequestResolver requestResolver) {
        this.chargeIssuer = chargeIssuer;
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
        ChargeRequest chargeRequest = new ChargeRequest(new ChargeTarget.ProgramStudent(enrollment), request.feeType(),
                java.time.YearMonth.from(request.dueDate()), request.dueDate(), IssueReason.AD_HOC, null, null, null, false, request.amount(),
                request.description(), subject, examBoardRegistration);
        return switch (chargeIssuer.issueInCurrentTransaction(chargeRequest)) {
            case IssueResult.Issued issued -> issued.invoice();
            case IssueResult.Rejected rejected -> throw new ChargeRejectedException(rejected.evaluation());
            case IssueResult.AlreadyIssued duplicated -> throw new com.red_escuela.backend.billing.exception.DuplicateChargeException("ad hoc");
        };
    }
}
