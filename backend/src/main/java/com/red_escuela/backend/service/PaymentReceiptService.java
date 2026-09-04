package com.red_escuela.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import com.red_escuela.backend.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Minimal placeholder — Fase 6.2 (Pagos y allocations) replaces "aprobar" with the real flow
// (PaymentService.registrarPago building a real Payment + PaymentAllocation, rejection with
// mandatory reason, unlimited retries). For now, approving sets the Invoice straight to PAID —
// a deliberate, commented bypass of Invoice's own state machine (Fase 6.1), acceptable only
// because this whole class is itself a temporary bridge until Fase 6.2 lands.
@Service
public class PaymentReceiptService {

    private final PaymentReceiptRepository paymentReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final FileStorageService fileStorageService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ObjectMapper objectMapper;

    public PaymentReceiptService(PaymentReceiptRepository paymentReceiptRepository, InvoiceRepository invoiceRepository,
                                  FileStorageService fileStorageService, CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
                                  CourseEnrollmentService courseEnrollmentService, ObjectMapper objectMapper) {
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.fileStorageService = fileStorageService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.objectMapper = objectMapper;
    }

    // studentId se recibe explícito, no se deriva de invoice.getCourseEnrollment()/
    // getProgramEnrollment() — el flujo de Épica 7 Fase 3 sube el comprobante contra una
    // Invoice ad-hoc todavía sin ningún enrollment vinculado (ver InvoiceFactory.crearAdHocPara*),
    // así que ese vínculo puede no existir todavía en este punto.
    @Transactional
    public PaymentReceipt subir(Long invoiceId, Long studentId, MultipartFile comprobante) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        String filePath = fileStorageService.store(studentId, "comprobantes", comprobante);

        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setInvoice(invoice);
        receipt.setFilePath(filePath);
        receipt.setStatus(PaymentReceiptStatus.PENDING_REVIEW);
        return paymentReceiptRepository.save(receipt);
    }

    @Transactional
    public PaymentReceipt aprobar(Long id, Person decidedBy) {
        PaymentReceipt receipt = paymentReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("PaymentReceipt not found: " + id));
        receipt.setStatus(PaymentReceiptStatus.APPROVED);
        receipt.setReviewedBy(decidedBy);
        receipt.setReviewedAt(LocalDateTime.now());

        Invoice invoice = receipt.getInvoice();
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        // A course has no separate "application" decision — approving the receipt of its first
        // installment is the only trigger for the real enrollment (business decision, same as
        // before: neither a program nor a course grants access until the payment is approved).
        courseEnrollmentApplicationRepository.findByPaymentReceiptId(id)
                .filter(application -> application.getCourseEnrollment() == null)
                .ifPresent(this::matricularCurso);

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
