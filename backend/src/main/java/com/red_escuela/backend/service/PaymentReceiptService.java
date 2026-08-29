package com.red_escuela.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.Payment;
import com.red_escuela.backend.entity.PaymentMethod;
import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentReceiptRepository;
import com.red_escuela.backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprobante de pago subido por el propio Alumno — paso previo sin confirmar
 * (ver {@link PaymentReceipt}). Al aprobar, construye el {@link Payment} real
 * (Factory Method, ver spec técnica) reusando el mismo cálculo de saldo que ya
 * usa {@link InvoiceService}/{@link PaymentService} para un pago cargado por
 * staff — un comprobante aprobado se convierte en un pago confirmado exactamente
 * igual que si Administración lo hubiera tipeado a mano. Desde la Fase 3 de
 * Épica 7, aprobar el comprobante de la primera cuota de un curso es también el
 * único disparador de la matriculación real ({@link CourseEnrollmentApplication}) —
 * curso no tiene una "solicitud" separada que alguien decida, el comprobante mismo
 * cumple ese rol (ver spec funcional, decisión de negocio).
 */
@Service
public class PaymentReceiptService {

    private final PaymentReceiptRepository paymentReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final FileStorageService fileStorageService;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ObjectMapper objectMapper;

    public PaymentReceiptService(
            PaymentReceiptRepository paymentReceiptRepository, InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository, InvoiceService invoiceService, FileStorageService fileStorageService,
            CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
            CourseEnrollmentService courseEnrollmentService, ObjectMapper objectMapper) {
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.fileStorageService = fileStorageService;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentReceipt subir(Long invoiceId, MultipartFile file) {
        Invoice invoice = invoiceService.findOrThrow(invoiceId);

        paymentReceiptRepository.findByInvoiceIdAndStatus(invoiceId, PaymentReceiptStatus.PENDING_REVIEW)
                .ifPresent(existing -> {
                    throw new PaymentReceiptAlreadyPendingException(invoiceId);
                });

        String relativePath = fileStorageService.store(invoice.getStudent().getId(), "comprobantes", file);

        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setInvoice(invoice);
        receipt.setFilePath(relativePath);
        receipt.setUploadedAt(LocalDateTime.now());
        receipt.setStatus(PaymentReceiptStatus.PENDING_REVIEW);
        return paymentReceiptRepository.save(receipt);
    }

    // Al aprobar, crea el Payment real por el monto total de la factura — un
    // comprobante siempre se sube por el monto completo de la cuota/matrícula
    // (no hay pago parcial vía comprobante en esta fase, a diferencia del
    // registro manual de PaymentService que sí lo permite).
    @Transactional
    public PaymentReceipt aprobar(Long id, Person reviewedBy) {
        PaymentReceipt receipt = findOrThrow(id);
        receipt.aprobar(reviewedBy);

        Invoice invoice = receipt.getInvoice();
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(invoice.getAmount());
        payment.setPaidAt(LocalDateTime.now());
        payment.setMethod(PaymentMethod.TRANSFER);
        payment.setRegisteredBy(reviewedBy);
        payment.setNotes("Generado a partir del comprobante " + receipt.getId());
        paymentRepository.save(payment);

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        // Si este comprobante es el de la primera cuota de una inscripción a curso
        // todavía sin matricular, recién ahora se dispara la matriculación real — ver
        // decisión de negocio "ni carrera ni curso otorgan acceso hasta que el pago se
        // aprueba" (spec funcional). Reusa matricularComoAutoservicio() sin tocarlo.
        courseEnrollmentApplicationRepository.findByPaymentReceiptId(id)
                .filter(intent -> intent.getCourseEnrollment() == null)
                .ifPresent(intent -> matricularCurso(intent));

        return receipt;
    }

    private void matricularCurso(CourseEnrollmentApplication intent) {
        Map<Long, Long> overrides = deserializarOverrides(intent.getSectionOverrides());
        CourseEnrollment enrollment = courseEnrollmentService.matricularComoAutoservicio(
                intent.getStudent().getId(), new CreateCourseEnrollmentRequest(intent.getCourse().getId(), overrides));
        intent.setCourseEnrollment(enrollment);
        courseEnrollmentApplicationRepository.save(intent);
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

    @Transactional
    public PaymentReceipt rechazar(Long id, Person reviewedBy, String reason) {
        PaymentReceipt receipt = findOrThrow(id);
        receipt.rechazar(reviewedBy, reason);
        return receipt;
    }

    public List<PaymentReceipt> listarPendientes() {
        return paymentReceiptRepository.findByStatusOrderByUploadedAtAsc(PaymentReceiptStatus.PENDING_REVIEW);
    }

    public List<PaymentReceipt> listarPorFactura(Long invoiceId) {
        return paymentReceiptRepository.findByInvoiceIdOrderByUploadedAtDesc(invoiceId);
    }

    private PaymentReceipt findOrThrow(Long id) {
        return paymentReceiptRepository.findById(id).orElseThrow(() -> new PaymentReceiptNotFoundException(id));
    }
}
