package com.red_escuela.backend.billing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.billing.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.billing.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.red_escuela.backend.billing.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.billing.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.service.CourseEnrollmentService;
import com.red_escuela.backend.service.FileStorageService;

@Service
public class PaymentReceiptService {

    private final PaymentReceiptRepository paymentReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final FileStorageService fileStorageService;
    private final PaymentReceiptPersister paymentReceiptPersister;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentReceiptService(PaymentReceiptRepository paymentReceiptRepository, InvoiceRepository invoiceRepository,
                                  FileStorageService fileStorageService, PaymentReceiptPersister paymentReceiptPersister,
                                  CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
                                  CourseEnrollmentService courseEnrollmentService, PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.fileStorageService = fileStorageService;
        this.paymentReceiptPersister = paymentReceiptPersister;
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
    //
    // Sin @Transactional acá a propósito (corrección 2026-09-04, auditoría): el upload real del
    // archivo es I/O potencialmente lento y no debe correr con el lock pesimista de la Invoice
    // sostenido — antes, todo el método (upload incluido) corría dentro de una única transacción
    // con findByIdForUpdate al principio, bloqueando cualquier otra operación sobre esa Invoice
    // (un pago, un ajuste, otro comprobante) mientras durara el upload. Ahora el upload corre
    // primero, sin transacción abierta, y PaymentReceiptPersister.persist (transacción propia,
    // corta) valida todo bajo lock justo antes de persistir.
    public PaymentReceipt subir(Long invoiceId, Long studentId, MultipartFile comprobante) {
        String filePath = fileStorageService.store(studentId, "comprobantes", comprobante);
        return paymentReceiptPersister.persist(invoiceId, studentId, filePath);
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
