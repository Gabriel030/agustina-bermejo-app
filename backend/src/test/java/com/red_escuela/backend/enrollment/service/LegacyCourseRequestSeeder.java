package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class LegacyCourseRequestSeeder {
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final InvoiceIssuingService invoiceIssuingService;
    private final UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    private final CourseEnrollmentApplicationRepository applicationRepository;
    private final Clock clock;

    public LegacyCourseRequestSeeder(StudentRepository studentRepository, CourseRepository courseRepository, InvoiceIssuingService invoiceIssuingService,
                                     UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase, CourseEnrollmentApplicationRepository applicationRepository, Clock clock) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.invoiceIssuingService = invoiceIssuingService;
        this.uploadPaymentReceiptUseCase = uploadPaymentReceiptUseCase;
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    @Transactional
    public CourseEnrollmentApplication requestWithLegacyReceipt(Long studentId, Long courseId) {
        return seed(studentId, courseId, true);
    }

    @Transactional
    public CourseEnrollmentApplication requestWithoutReceipt(Long studentId, Long courseId) {
        return seed(studentId, courseId, false);
    }

    private CourseEnrollmentApplication seed(Long studentId, Long courseId, boolean withReceipt) {
        Student student = studentRepository.findById(studentId).orElseThrow();
        Course course = courseRepository.findById(courseId).orElseThrow();
        Invoice invoice = switch (invoiceIssuingService.issueInvoiceInCallerTransaction(InvoiceIssuingRequestFactory.createCourseAdmissionRequest(studentId, course, LocalDate.now(clock)))) {
            case InvoiceIssuingResult.InvoiceIssued issued -> issued.invoice();
            default -> throw new IllegalStateException("The admission invoice was not issued");
        };
        CourseEnrollmentApplication application = new CourseEnrollmentApplication();
        application.setStudent(student);
        application.setCourse(course);
        application.setInvoiceId(invoice.getId());
        if (withReceipt) {
            application.setPaymentReceipt(uploadPaymentReceiptUseCase.upload(invoice.getId(), studentId,
                    new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes())));
        }
        return applicationRepository.save(application);
    }
}
