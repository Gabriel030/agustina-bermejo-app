package com.red_escuela.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.dto.CreateInvoiceFromFeeScheduleRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Solicitud de inscripción a un curso con pago de la primera cuota (Épica 7 Fase 3).
 * Servicio separado de {@link CourseEnrollmentService} (SRP: acá se orquesta
 * Invoice+PaymentReceipt+CourseEnrollmentApplication, no la matriculación en sí) y
 * de {@link PaymentReceiptService} (evita un ciclo — PaymentReceiptService ya
 * depende de CourseEnrollmentService para disparar la matriculación real al
 * approve, así que este service es el único punto que depende de los tres).
 * Reusa {@link CourseEnrollmentService#validateCanEnroll} para no pedirle un
 * comprobante a alguien que de todas formas no podría enroll (ventana
 * cerrada, ya inscripto, etc.) — misma validación que enroll() directo.
 */
@Service
public class CourseEnrollmentApplicationService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final InvoiceService invoiceService;
    private final PaymentReceiptService paymentReceiptService;
    private final ObjectMapper objectMapper;

    public CourseEnrollmentApplicationService(
            StudentRepository studentRepository, CourseRepository courseRepository,
            CourseEnrollmentService courseEnrollmentService,
            CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
            InvoiceService invoiceService, PaymentReceiptService paymentReceiptService, ObjectMapper objectMapper) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.invoiceService = invoiceService;
        this.paymentReceiptService = paymentReceiptService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CourseEnrollmentApplication requestWithPayment(Long studentId, CreateCourseEnrollmentRequest request, MultipartFile receiptFile) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        courseEnrollmentService.validateCanEnroll(student, course);

        Invoice invoice = invoiceService.createFromFeeSchedule(studentId,
                new CreateInvoiceFromFeeScheduleRequest(null, request.courseId(), FeeScheduleConcept.CUOTA_MENSUAL, LocalDate.now()));
        PaymentReceipt receipt = paymentReceiptService.upload(invoice.getId(), receiptFile);

        CourseEnrollmentApplication application = new CourseEnrollmentApplication();
        application.setStudent(student);
        application.setCourse(course);
        application.setSectionOverrides(serializeOverrides(request.sectionOverrides()));
        application.setPaymentReceipt(receipt);
        return courseEnrollmentApplicationRepository.save(application);
    }

    public List<CourseEnrollmentApplication> listOwn(Long studentId) {
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
