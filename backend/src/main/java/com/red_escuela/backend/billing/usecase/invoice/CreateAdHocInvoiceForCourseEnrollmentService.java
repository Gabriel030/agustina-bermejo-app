package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.service.AdHocInvoiceRequestResolver;
import com.red_escuela.backend.billing.service.InvoiceFactory;
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

    private final InvoiceFactory invoiceFactory;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final AdHocInvoiceRequestResolver requestResolver;

    public CreateAdHocInvoiceForCourseEnrollmentService(InvoiceFactory invoiceFactory, CourseEnrollmentRepository courseEnrollmentRepository,
                                                          AdHocInvoiceRequestResolver requestResolver) {
        this.invoiceFactory = invoiceFactory;
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
        return invoiceFactory.createAdHocFeeForCourseEnrollment(
                enrollment, request.feeType(), request.amount(), request.dueDate(), request.description(), subject, examBoardRegistration);
    }
}
