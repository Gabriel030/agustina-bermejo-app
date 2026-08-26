package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateInvoiceRequest;
import com.red_escuela.backend.dto.DebtSummaryResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.PaymentStanding;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.InvoiceAlreadyCancelledException;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-AL-09/RF-AD-08: facturación interna sin pasarela (decisión 1) — el estado de deuda de un
 * alumno nunca es un campo persistido, siempre se calcula on-demand a partir de Invoice/Payment
 * (decisión 3), reemplazando el rol que cumplía el campo muerto Student.defaulter.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository, PaymentRepository paymentRepository, StudentRepository studentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public Invoice crear(Long studentId, CreateInvoiceRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        Invoice invoice = new Invoice();
        invoice.setStudent(student);
        invoice.setConcept(request.concept());
        invoice.setAmount(request.amount());
        invoice.setDueDate(request.dueDate());
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    public List<InvoiceResponse> listarPorAlumno(Long studentId) {
        return invoiceRepository.findByStudentId(studentId).stream()
                .map(invoice -> InvoiceResponse.from(invoice, calcularSaldoPendiente(invoice)))
                .toList();
    }

    @Transactional
    public void cancelar(Long invoiceId) {
        Invoice invoice = findOrThrow(invoiceId);
        // No se puede cancelar una factura ya saldada (PAID) ni una ya CANCELLED.
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvoiceAlreadyCancelledException(invoiceId);
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
    }

    // Decisión 3: siempre calculado on-demand, nunca un campo persistido — una factura recién
    // pagada se refleja inmediatamente, sin job ni sincronización posterior.
    public DebtSummaryResponse calcularDeuda(Long studentId) {
        List<Invoice> pendientes = invoiceRepository.findByStudentIdAndStatusIn(
                studentId, List.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE));

        BigDecimal montoAdeudado = pendientes.stream()
                .map(this::calcularSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<InvoiceResponse> facturasPendientes = pendientes.stream()
                .map(invoice -> InvoiceResponse.from(invoice, calcularSaldoPendiente(invoice)))
                .toList();

        return new DebtSummaryResponse(montoAdeudado.compareTo(BigDecimal.ZERO) > 0, montoAdeudado, facturasPendientes);
    }

    /**
     * Decisión 17 — punto de extensión a futuro para una validación de inscripción, sin ningún
     * caller de bloqueo todavía (ver Deuda técnica de la spec). OVERDUE hoy no lo setea ningún
     * job (ver Deuda técnica): una factura PENDING/PARTIALLY_PAID con dueDate ya pasada cuenta
     * como vencida acá mismo, on-demand, sin necesitar que la base ya la tenga en OVERDUE.
     */
    public PaymentStanding calcularEstadoDePago(Long studentId) {
        List<Invoice> todasLasFacturas = invoiceRepository.findByStudentId(studentId);
        if (todasLasFacturas.isEmpty()) {
            return PaymentStanding.SIN_INFORMACION;
        }
        LocalDate hoy = LocalDate.now();
        boolean hayVencidaImpaga = todasLasFacturas.stream().anyMatch(invoice ->
                invoice.getStatus() == InvoiceStatus.OVERDUE
                        || ((invoice.getStatus() == InvoiceStatus.PENDING || invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                                && invoice.getDueDate().isBefore(hoy)));
        return hayVencidaImpaga ? PaymentStanding.MOROSO : PaymentStanding.AL_DIA;
    }

    BigDecimal calcularSaldoPendiente(Invoice invoice) {
        BigDecimal pagado = paymentRepository.findByInvoiceId(invoice.getId()).stream()
                .map(payment -> payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoice.getAmount().subtract(pagado);
    }

    Invoice findOrThrow(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }
}
