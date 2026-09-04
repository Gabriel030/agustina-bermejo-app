package com.red_escuela.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.dto.PaymentAllocationRequest;
import com.red_escuela.backend.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.PaymentMethod;
import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.InvoiceNotPayableException;
import com.red_escuela.backend.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentReceiptRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PaymentReceiptService {

    private final PaymentReceiptRepository paymentReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final StudentRepository studentRepository;
    private final FileStorageService fileStorageService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentReceiptService(PaymentReceiptRepository paymentReceiptRepository, InvoiceRepository invoiceRepository,
                                  StudentRepository studentRepository, FileStorageService fileStorageService,
                                  CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
                                  CourseEnrollmentService courseEnrollmentService, PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.studentRepository = studentRepository;
        this.fileStorageService = fileStorageService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    // studentId se recibe explícito, no se deriva de invoice.getCourseEnrollment()/
    // getProgramEnrollment() — el flujo de Épica 7 Fase 3 sube el comprobante contra una
    // Invoice ad-hoc todavía sin ningún enrollment vinculado (ver InvoiceFactory.crearAdHocPara*),
    // así que ese vínculo puede no existir todavía en este punto — en ese caso, ownership no se
    // puede validar contra la Invoice y se confía en el caller (ya resolvió el Student por su
    // cuenta antes de llegar acá).
    @Transactional
    public PaymentReceipt subir(Long invoiceId, Long studentId, MultipartFile comprobante) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        validateOwnership(invoice, studentId);
        if (invoice.getStatus() != InvoiceStatus.PENDING && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus().name());
        }
        if (paymentReceiptRepository.existsByInvoiceIdAndStatus(invoiceId, PaymentReceiptStatus.PENDING_REVIEW)) {
            throw new PaymentReceiptAlreadyPendingException(invoiceId);
        }
        String filePath = fileStorageService.store(studentId, "comprobantes", comprobante);

        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setInvoice(invoice);
        receipt.setFilePath(filePath);
        receipt.setStatus(PaymentReceiptStatus.PENDING_REVIEW);
        receipt.setUploadedBy(studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId)).getPerson());
        return paymentReceiptRepository.save(receipt);
    }

    private void validateOwnership(Invoice invoice, Long studentId) {
        Long ownerId = invoice.getCourseEnrollment() != null ? invoice.getCourseEnrollment().getStudent().getId()
                : invoice.getProgramEnrollment() != null ? invoice.getProgramEnrollment().getStudent().getId()
                : null;
        if (ownerId != null && !ownerId.equals(studentId)) {
            throw new InvoiceNotFoundException(invoice.getId());
        }
    }

    @Transactional
    public PaymentReceipt aprobar(Long id, Person decidedBy) {
        PaymentReceipt receipt = findOrThrow(id);
        if (receipt.getStatus() != PaymentReceiptStatus.PENDING_REVIEW) {
            throw new InvalidPaymentReceiptStateException(id, receipt.getStatus().name());
        }
        receipt.setStatus(PaymentReceiptStatus.APPROVED);
        receipt.setReviewedBy(decidedBy);
        receipt.setReviewedAt(LocalDateTime.now());

        // Mismo camino que un pago cargado a mano por Administrativo — el comprobante no
        // distingue efectivo/transferencia, siempre es "subí un archivo", así que method =
        // TRANSFER. PaymentService.registerPayment ya deja la Invoice en PAID/PARTIALLY_PAID
        // según corresponda; no se toca acá.
        Invoice invoice = receipt.getInvoice();
        paymentService.registerPayment(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), invoice.getRemainingBalance())),
                PaymentMethod.TRANSFER, false, null, null, null, null), decidedBy);

        // A course has no separate "application" decision — approving the receipt of its first
        // installment is the only trigger for the real enrollment (business decision, same as
        // before: neither a program nor a course grants access until the payment is approved).
        courseEnrollmentApplicationRepository.findByPaymentReceiptId(id)
                .filter(application -> application.getCourseEnrollment() == null)
                .ifPresent(this::matricularCurso);

        return paymentReceiptRepository.save(receipt);
    }

    @Transactional
    public PaymentReceipt reject(Long id, String motivo, Person decidedBy) {
        PaymentReceipt receipt = findOrThrow(id);
        if (receipt.getStatus() != PaymentReceiptStatus.PENDING_REVIEW) {
            throw new InvalidPaymentReceiptStateException(id, receipt.getStatus().name());
        }
        receipt.setStatus(PaymentReceiptStatus.REJECTED);
        receipt.setRejectionReason(motivo);
        receipt.setReviewedBy(decidedBy);
        receipt.setReviewedAt(LocalDateTime.now());
        // La Invoice vuelve a estar disponible para un comprobante nuevo — reintentos ilimitados
        // (el índice único de "pending" solo bloquea mientras ESTE queda PENDING_REVIEW).
        return paymentReceiptRepository.save(receipt);
    }

    private void matricularCurso(CourseEnrollmentApplication application) {
        Map<Long, Long> overrides = deserializarOverrides(application.getSectionOverrides());
        CourseEnrollment enrollment = courseEnrollmentService.matricularComoAutoservicio(
                application.getStudent().getId(), new CreateCourseEnrollmentRequest(application.getCourse().getId(), overrides));
        application.setCourseEnrollment(enrollment);
        courseEnrollmentApplicationRepository.save(application);

        // Only now does the CourseEnrollment exist — the first-installment invoice (created
        // ad-hoc when the application was submitted, with no enrollment link possible yet) gets
        // linked retroactively, so the recurring billing engine (Fase 6.1) doesn't generate a
        // duplicate for the month already paid this way.
        Invoice invoice = application.getPaymentReceipt().getInvoice();
        invoice.setCourseEnrollment(enrollment);
        invoiceRepository.save(invoice);
    }

    public List<PaymentReceipt> listarPendientes() {
        return paymentReceiptRepository.findByStatusOrderByUploadedAtAsc(PaymentReceiptStatus.PENDING_REVIEW);
    }

    public List<PaymentReceipt> findOwn(Long studentId) {
        return paymentReceiptRepository.findByStudentId(studentId);
    }

    private PaymentReceipt findOrThrow(Long id) {
        return paymentReceiptRepository.findById(id).orElseThrow(() -> new PaymentReceiptNotFoundException(id));
    }

    private Map<Long, Long> deserializarOverrides(String sectionOverridesJson) {
        if (sectionOverridesJson == null || sectionOverridesJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(sectionOverridesJson, new com.fasterxml.jackson.core.type.TypeReference<Map<Long, Long>>() {
            });
        } catch (JsonProcessingException e) {
            throw new InvalidSectionOverridesException(sectionOverridesJson, e);
        }
    }
}
