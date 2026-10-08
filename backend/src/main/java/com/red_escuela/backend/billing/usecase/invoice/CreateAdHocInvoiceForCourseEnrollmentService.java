package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.service.AdHocInvoiceRequestResolver;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.billing.service.InvoiceViewLoader;
import com.red_escuela.backend.billing.service.InvoiceView;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateAdHocInvoiceForCourseEnrollmentService implements CreateAdHocInvoiceForCourseEnrollmentUseCase {

    private final InvoiceIssuingService invoiceIssuingService;
    private final InvoiceViewLoader invoiceViewLoader;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final AdHocInvoiceRequestResolver requestResolver;

    public CreateAdHocInvoiceForCourseEnrollmentService(InvoiceIssuingService invoiceIssuingService, InvoiceViewLoader invoiceViewLoader, CourseEnrollmentRepository courseEnrollmentRepository,
                                                          AdHocInvoiceRequestResolver requestResolver) {
        this.invoiceIssuingService = invoiceIssuingService;
        this.invoiceViewLoader = invoiceViewLoader;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.requestResolver = requestResolver;
    }

    @Override
    @Transactional
    public InvoiceView create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request) {
        return create(courseEnrollmentId, request, java.util.UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public InvoiceView create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request, String idempotencyKey) {
        requestResolver.validateFeeType(request.feeType());
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        if (request.amount().currency() != enrollment.getCourse().getCurrency()) {
            throw new CurrencyMismatchException(enrollment.getCourse().getCurrency(), request.amount().currency());
        }
        Long subjectId = requestResolver.resolveSubject(request.subjectId()) == null ? null : request.subjectId();
        Long examBoardRegistrationId = requestResolver.resolveExamBoardRegistration(request.examBoardRegistrationId()) == null ? null : request.examBoardRegistrationId();
        var chargeRequest = InvoiceIssuingRequestFactory.createCourseAdHocRequest(enrollment, request.feeType(), request.dueDate(), NonNegativeMoney.of(request.amount()),
                request.description(), subjectId, examBoardRegistrationId, idempotencyKey);
        return switch (invoiceIssuingService.issueInvoiceInCallerTransaction(chargeRequest)) {
            case InvoiceIssuingResult.InvoiceIssued issued -> invoiceViewLoader.loadInvoiceView(issued.invoice());
            case InvoiceIssuingResult.InvoiceIssuingRejected rejected -> throw new ChargeRejectedException(rejected.evaluationResult());
            case InvoiceIssuingResult.InvoiceAlreadyIssued duplicated -> throw new com.red_escuela.backend.billing.exception.DuplicateChargeException("ad hoc");
        };
    }
}
