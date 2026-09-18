package com.red_escuela.backend.enrollment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import com.red_escuela.backend.billing.service.InvoiceFactory;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;

@Service
public class CourseEnrollmentApplicationService {
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final InvoiceFactory invoiceFactory;
    private final UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    private final ObjectMapper objectMapper;

    public CourseEnrollmentApplicationService(
            StudentRepository studentRepository, CourseRepository courseRepository,
            CourseEnrollmentService courseEnrollmentService,
            CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
            InvoiceFactory invoiceFactory, UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase, ObjectMapper objectMapper) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.invoiceFactory = invoiceFactory;
        this.uploadPaymentReceiptUseCase = uploadPaymentReceiptUseCase;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CourseEnrollmentApplication requestWithPayment(Long studentId, CreateCourseEnrollmentRequest request, MultipartFile comprobante) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        courseEnrollmentService.validateCanEnroll(student, course);

        Invoice invoice = invoiceFactory.crearAdHocParaCurso(course, FeeType.RECURRING_FEE, YearMonth.now(), LocalDate.now());
        PaymentReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), studentId, comprobante);

        CourseEnrollmentApplication application = new CourseEnrollmentApplication();
        application.setStudent(student);
        application.setCourse(course);
        application.setSectionOverrides(serializeOverrides(request.sectionOverrides()));
        application.setPaymentReceipt(receipt);
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
