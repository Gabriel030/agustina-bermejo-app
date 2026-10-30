package com.red_escuela.backend.enrollment.service;

import java.time.Clock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.enrollment.exception.PendingCourseEnrollmentApplicationException;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.enrollment.exception.DniRequiredException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.api.StudentFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.payment.usecase.SubmitCourseAdmissionReceiptUseCase;
import com.red_escuela.backend.enrollment.dto.CourseTransferReceiptForm;
import jakarta.persistence.EntityManager;

@Service
public class CourseEnrollmentApplicationService {
    private final StudentFinder studentFinder;
    private final CourseCatalogFinder courseCatalogFinder;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final InvoiceIssuingService invoiceIssuingService;
    private final SubmitCourseAdmissionReceiptUseCase submitCourseAdmissionReceiptUseCase;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CourseEnrollmentApplicationService(StudentFinder studentFinder, CourseCatalogFinder courseCatalogFinder,
            CourseEnrollmentService courseEnrollmentService,
            CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
            InvoiceIssuingService invoiceIssuingService, SubmitCourseAdmissionReceiptUseCase submitCourseAdmissionReceiptUseCase, EntityManager entityManager,
            ObjectMapper objectMapper, Clock clock) {
        this.studentFinder = studentFinder;
        this.courseCatalogFinder = courseCatalogFinder;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.invoiceIssuingService = invoiceIssuingService;
        this.submitCourseAdmissionReceiptUseCase = submitCourseAdmissionReceiptUseCase;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public CourseEnrollmentApplication requestWithPayment(Long studentId, CreateCourseEnrollmentRequest request,
            CourseTransferReceiptForm receipt, String idempotencyKey) {
        Student student = studentFinder.requireById(studentId);
        String dni = student.getPerson().getDni();
        if (dni == null || dni.isBlank()) {
            throw new DniRequiredException(student.getPerson().getId());
        }
        Course course = courseCatalogFinder.findCourseEntity(request.courseId());
        courseEnrollmentService.validateCanEnroll(student, course.getId());
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
        Long invoiceId = invoice.getId();
        application.setInvoiceId(invoiceId);
        if (!invoice.isPayable()) {
            application.setCourseEnrollment(courseEnrollmentService.enrollSelfService(studentId, request));
            invoice.assignInvoiceToCourseEnrollment(application.getCourseEnrollment().getId());
            return courseEnrollmentApplicationRepository.save(application);
        }
        Long receiptId = submitCourseAdmissionReceiptUseCase.submitReceiptCoveringCourseAdmission(studentId, invoiceId, receipt.file(), receipt.olderInvoiceIds(),
                receipt.declaredAmount(), receipt.transferDate(), receipt.operationNumber(), receipt.originAccount(), receipt.holderName(),
                receipt.bankName(), idempotencyKey);
        CourseEnrollmentApplication saved = courseEnrollmentApplicationRepository.save(application);
        courseEnrollmentApplicationRepository.linkPaymentReceipt(saved.getId(), receiptId);
        entityManager.refresh(saved);
        return saved;
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
