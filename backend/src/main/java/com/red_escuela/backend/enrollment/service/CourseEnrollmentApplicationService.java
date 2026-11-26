package com.red_escuela.backend.enrollment.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.enrollment.api.CourseAdmissionInvoicePort;
import com.red_escuela.backend.enrollment.api.CourseAdmissionInvoiceView;
import com.red_escuela.backend.enrollment.api.CourseAdmissionReceiptSubmissionPort;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.enrollment.exception.PendingCourseEnrollmentApplicationException;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.enrollment.exception.DniRequiredException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.identity.api.StudentFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import com.red_escuela.backend.enrollment.dto.CourseTransferReceiptForm;
import jakarta.persistence.EntityManager;

@Service
public class CourseEnrollmentApplicationService {
    private final StudentFinder studentFinder;
    private final CourseCatalogFinder courseCatalogFinder;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final CourseAdmissionInvoicePort courseAdmissionInvoicePort;
    private final CourseAdmissionReceiptSubmissionPort courseAdmissionReceiptSubmissionPort;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public CourseEnrollmentApplicationService(StudentFinder studentFinder, CourseCatalogFinder courseCatalogFinder,
            CourseEnrollmentService courseEnrollmentService,
            CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
            CourseAdmissionInvoicePort courseAdmissionInvoicePort, CourseAdmissionReceiptSubmissionPort courseAdmissionReceiptSubmissionPort, EntityManager entityManager,
            ObjectMapper objectMapper) {
        this.studentFinder = studentFinder;
        this.courseCatalogFinder = courseCatalogFinder;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.courseAdmissionInvoicePort = courseAdmissionInvoicePort;
        this.courseAdmissionReceiptSubmissionPort = courseAdmissionReceiptSubmissionPort;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
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
        courseEnrollmentService.validateCanRequestAsStudent(student, course.getId());
        if (courseEnrollmentApplicationRepository.existsByStudentIdAndCourseIdAndPaymentReceiptStatus(
                studentId, course.getId(), BankTransferReceiptStatus.PENDING_REVIEW)) {
            throw new PendingCourseEnrollmentApplicationException(studentId, course.getId());
        }

        CourseAdmissionInvoiceView invoice = courseAdmissionInvoicePort.issueCourseAdmissionInvoice(studentId, course.getId());
        CourseEnrollmentApplication application = new CourseEnrollmentApplication();
        application.setStudent(student);
        application.setCourse(course);
        application.setSectionOverrides(serializeOverrides(request.sectionOverrides()));
        Long invoiceId = invoice.invoiceId();
        application.setInvoiceId(invoiceId);
        if (!invoice.payable()) {
            application.setCourseEnrollment(courseEnrollmentService.enrollSelfService(studentId, request));
            courseAdmissionInvoicePort.assignInvoiceToCourseEnrollment(invoiceId, application.getCourseEnrollment().getId());
            return courseEnrollmentApplicationRepository.save(application);
        }
        Long receiptId = courseAdmissionReceiptSubmissionPort.submitReceiptCoveringCourseAdmission(studentId, invoiceId, receipt.file(), receipt.olderInvoiceIds(),
                receipt.declaredAmount(), receipt.transferDate(), receipt.operationNumber(), receipt.originAccount(), receipt.holderName(),
                receipt.bankName(), idempotencyKey);
        CourseEnrollmentApplication saved = courseEnrollmentApplicationRepository.save(application);
        courseEnrollmentApplicationRepository.linkPaymentReceipt(saved.getId(), receiptId);
        entityManager.refresh(saved);
        return saved;
    }

    @Transactional
    public CourseEnrollment enrollByStaff(Long studentId, CreateCourseEnrollmentRequest request) {
        CourseEnrollment enrollment = courseEnrollmentService.enroll(studentId, request);
        if (enrollment.getStatus() != EnrollmentStatus.WAITLISTED) {
            return enrollment;
        }
        Course course = enrollment.getCourse();
        Long invoiceId = courseAdmissionInvoicePort.issueCourseAdmissionInvoice(studentId, course.getId()).invoiceId();
        CourseEnrollmentApplication application = new CourseEnrollmentApplication();
        application.setStudent(enrollment.getStudent());
        application.setCourse(course);
        application.setSectionOverrides(serializeOverrides(request.sectionOverrides()));
        application.setInvoiceId(invoiceId);
        application.setCourseEnrollment(enrollment);
        courseEnrollmentApplicationRepository.save(application);
        return enrollment;
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
        } catch (JacksonException e) {
            throw new InvalidSectionOverridesException(String.valueOf(sectionOverrides), e);
        }
    }
}
