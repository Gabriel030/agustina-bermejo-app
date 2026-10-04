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
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateAdHocInvoiceForCourseEnrollmentService implements CreateAdHocInvoiceForCourseEnrollmentUseCase {

    private final ChargeIssuer chargeIssuer;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final AdHocInvoiceRequestResolver requestResolver;

    public CreateAdHocInvoiceForCourseEnrollmentService(ChargeIssuer chargeIssuer, CourseEnrollmentRepository courseEnrollmentRepository,
                                                          AdHocInvoiceRequestResolver requestResolver) {
        this.chargeIssuer = chargeIssuer;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.requestResolver = requestResolver;
    }

    @Override
    @Transactional
    public Invoice create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request) {
        requestResolver.validateFeeType(request.feeType());
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        if (request.amount().currency() != enrollment.getCourse().getCurrency()) {
            throw new CurrencyMismatchException(enrollment.getCourse().getCurrency(), request.amount().currency());
        }
        Subject subject = requestResolver.resolveSubject(request.subjectId());
        ExamBoardRegistration examBoardRegistration = requestResolver.resolveExamBoardRegistration(request.examBoardRegistrationId());
        ChargeRequest chargeRequest = new ChargeRequest(new ChargeTarget.CourseStudent(enrollment), request.feeType(),
                java.time.YearMonth.from(request.dueDate()), request.dueDate(), IssueReason.AD_HOC, null, null, null, false, request.amount(),
                request.description(), subject, examBoardRegistration);
        return switch (chargeIssuer.issueInCurrentTransaction(chargeRequest)) {
            case IssueResult.Issued issued -> issued.invoice();
            case IssueResult.Rejected rejected -> throw new ChargeRejectedException(rejected.evaluation());
            case IssueResult.AlreadyIssued duplicated -> throw new com.red_escuela.backend.billing.exception.DuplicateChargeException("ad hoc");
        };
    }
}
