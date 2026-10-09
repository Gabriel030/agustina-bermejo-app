package com.red_escuela.backend.enrollment.service;

import java.time.Clock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.enrollment.exception.PendingCourseEnrollmentApplicationException;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.enrollment.exception.DniRequiredException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.enrollment.exception.PaymentReceiptRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;

@Service
public class CourseEnrollmentApplicationService {
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final InvoiceIssuingService invoiceIssuingService;
    private final UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CourseEnrollmentApplicationService(StudentRepository studentRepository, CourseRepository courseRepository,
            CourseEnrollmentService courseEnrollmentService,
            CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
            InvoiceIssuingService invoiceIssuingService, UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase, ObjectMapper objectMapper,
            Clock clock) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.invoiceIssuingService = invoiceIssuingService;
        this.uploadPaymentReceiptUseCase = uploadPaymentReceiptUseCase;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public CourseEnrollmentApplication requestWithPayment(Long studentId, CreateCourseEnrollmentRequest request, MultipartFile comprobante) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        String dni = student.getPerson().getDni();
        if (dni == null || dni.isBlank()) {
            throw new DniRequiredException(student.getPerson().getId());
        }
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        courseEnrollmentService.validateCanEnroll(student, course);
        if (courseEnrollmentApplicationRepository.existsByStudentIdAndCourseIdAndPaymentReceiptStatus(
                studentId, course.getId(), BankTransferReceiptStatus.PENDING_REVIEW)) {
            throw new PendingCourseEnrollmentApplicationException(studentId, course.getId());
        }

        Invoice invoice = switch (invoiceIssuingService.issueInvoiceInCallerTransaction(InvoiceIssuingRequestFactory.createCourseAdmissionRequest(studentId, course, LocalDate.now(clock)))) {
            case InvoiceIssuingResult.InvoiceIssued issued -> issued.invoice();
            case InvoiceIssuingResult.InvoiceIssuingRejected rejected -> throw new ChargeRejectedException(rejected.evaluationResult());
            case InvoiceIssuingResult.InvoiceAlreadyIssued duplicated -> throw new com.red_escuela.backend.billing.exception.DuplicateChargeException("course admission");
        };
        CourseEnrollmentApplication application = new CourseEnrollmentApplication();
        application.setStudent(student);
        application.setCourse(course);
        application.setSectionOverrides(serializeOverrides(request.sectionOverrides()));
        application.setInvoiceId(invoice.getId());
        if (!invoice.isPayable()) {
            application.setCourseEnrollment(courseEnrollmentService.enrollSelfService(studentId, request));
            invoice.assignInvoiceToCourseEnrollment(application.getCourseEnrollment().getId());
            return courseEnrollmentApplicationRepository.save(application);
        }
        if (comprobante == null || comprobante.isEmpty()) {
            throw new PaymentReceiptRequiredException(invoice.getId());
        }
        application.setPaymentReceipt(uploadPaymentReceiptUseCase.upload(invoice.getId(), studentId, comprobante));
        return courseEnrollmentApplicationRepository.save(application);
    }

    public List<CourseEnrollmentApplication> listMine(Long studentId) {
        return courseEnrollmentApplicationRepository.findByStudentIdOrderByIdDesc(studentId);
    }

    private String serializeOverrides(Map<Long, Long> sectionOverrides) {
        if (sectionOverrides == null || sectionOverrides.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sectionOverrides);
        } catch (JsonProcessingException e) {
            throw new InvalidSectionOverridesException(String.valueOf(sectionOverrides), e);
        }
    }
}
