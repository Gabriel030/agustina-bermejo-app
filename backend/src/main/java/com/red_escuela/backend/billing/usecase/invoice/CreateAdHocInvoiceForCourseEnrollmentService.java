package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.service.AdHocInvoiceRequestResolver;
import com.red_escuela.backend.billing.service.InvoiceFactory;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;

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
    public Invoice create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request) {
        requestResolver.validateFeeType(request.feeType());
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        Subject subject = requestResolver.resolveSubject(request.subjectId());
        ExamBoardRegistration examBoardRegistration = requestResolver.resolveExamBoardRegistration(request.examBoardRegistrationId());
        return invoiceFactory.createAdHocFeeForCourseEnrollment(
                enrollment, request.feeType(), request.amount(), request.dueDate(), request.description(), subject, examBoardRegistration);
    }
}
