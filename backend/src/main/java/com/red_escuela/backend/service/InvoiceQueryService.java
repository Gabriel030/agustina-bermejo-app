package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.PriceBreakdownLineResponse;
import com.red_escuela.backend.dto.PriceBreakdownPaymentResponse;
import com.red_escuela.backend.dto.PriceBreakdownResponse;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceAdjustment;
import com.red_escuela.backend.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.PaymentAllocation;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public InvoiceQueryService(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                ProgramEnrollmentRepository programEnrollmentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    public List<Invoice> porCurso(Long courseEnrollmentId) {
        return invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollmentId);
    }

    public List<Invoice> porCarrera(Long programEnrollmentId) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId);
    }

    public List<Invoice> porCursoPropio(Long studentId, Long courseEnrollmentId) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, courseEnrollmentId);
        }
        return invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollmentId);
    }

    // "Transparencia de precio" (Fase 6.2): no es un cálculo nuevo, ordena y le pone nombre
    // humano-legible a datos que ya existen (baseAmount, cada InvoiceAdjustment,
    // cada PaymentAllocation) — el pipeline de líneas sigue el orden
    // SCHOLARSHIP/PROMOTION/DISCOUNT/SURCHARGE aunque esta fase solo ejercite DISCOUNT/SURCHARGE
    // (SCHOLARSHIP/PROMOTION se completan en Fases 6.4/6.5 sin cambiar este DTO).
    public PriceBreakdownResponse priceBreakdown(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        List<PriceBreakdownLineResponse> lines = invoice.getAdjustments().stream()
                .sorted((a, b) -> Integer.compare(pipelineOrder(a.getType()), pipelineOrder(b.getType())))
                .map(this::toLine)
                .toList();
        List<PriceBreakdownPaymentResponse> payments = invoice.getAllocations().stream()
                .map(this::toPayment)
                .toList();
        return new PriceBreakdownResponse(
                invoice.getId(), invoice.getFeeType().name(), invoice.getPeriod().toString(), invoice.getBaseAmount(),
                lines, invoice.getAmountDue(), payments, invoice.getRemainingBalance(), invoice.getStatus().name());
    }

    public PriceBreakdownResponse ownPriceBreakdown(Long studentId, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        Long ownerId = invoice.getCourseEnrollment() != null ? invoice.getCourseEnrollment().getStudent().getId()
                : invoice.getProgramEnrollment() != null ? invoice.getProgramEnrollment().getStudent().getId()
                : null;
        if (!studentId.equals(ownerId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        return priceBreakdown(invoiceId);
    }

    private int pipelineOrder(InvoiceAdjustmentType type) {
        return switch (type) {
            case SCHOLARSHIP -> 0;
            case PROMOTION -> 1;
            case DISCOUNT, WAIVE_DEBT -> 2;
            case SURCHARGE -> 3;
        };
    }

    private PriceBreakdownLineResponse toLine(InvoiceAdjustment adjustment) {
        Person appliedBy = adjustment.getAppliedBy();
        String source = appliedBy != null ? appliedBy.getName() + " " + appliedBy.getLastname() : "Sistema (mora automática)";
        return new PriceBreakdownLineResponse(
                adjustment.getType().name(), adjustment.signedAmount(), adjustment.getReason(), source, adjustment.getAppliedAt());
    }

    private PriceBreakdownPaymentResponse toPayment(PaymentAllocation allocation) {
        return new PriceBreakdownPaymentResponse(
                allocation.getPayment().getId(), allocation.getAmountApplied(),
                allocation.getPayment().getMethod().name(), allocation.getPayment().getPaidAt());
    }

    public List<Invoice> porCarreraPropia(Long studentId, Long programEnrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, programEnrollmentId);
        }
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId);
    }

    // Replaces the old InvoiceService.calcularEstadoDePago(studentId) removed in Fase 6.0 — the
    // real billingStanding state machine lands in Fase 6.3. Used by GraduationRequestService
    // (decisión 10): egreso exige saldo cero, cualquier cuota sin pagar bloquea el trámite, sin
    // importar si todavía no venció.
    public boolean tieneDeuda(Long studentId) {
        for (CourseEnrollment enrollment : courseEnrollmentRepository.findByStudentId(studentId)) {
            if (tieneAlgunaCuotaSinPagar(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()))) {
                return true;
            }
        }
        for (ProgramEnrollment enrollment : programEnrollmentRepository.findByStudentId(studentId)) {
            if (tieneAlgunaCuotaSinPagar(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()))) {
                return true;
            }
        }
        return false;
    }

    // Usado por CertificateService (constancia de alumno regular, decisión 17): a diferencia de
    // tieneDeuda, tolera una cuota futura todavía no vencida — "regular" exige no estar
    // atrasado, no exige saldo cero.
    public boolean tieneDeudaVencida(Long studentId) {
        LocalDate today = LocalDate.now();
        for (CourseEnrollment enrollment : courseEnrollmentRepository.findByStudentId(studentId)) {
            if (invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).stream().anyMatch(i -> estaVencida(i, today))) {
                return true;
            }
        }
        for (ProgramEnrollment enrollment : programEnrollmentRepository.findByStudentId(studentId)) {
            if (invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream().anyMatch(i -> estaVencida(i, today))) {
                return true;
            }
        }
        return false;
    }

    private boolean tieneAlgunaCuotaSinPagar(List<Invoice> invoices) {
        return invoices.stream().anyMatch(i -> i.getStatus() == InvoiceStatus.PENDING || i.getStatus() == InvoiceStatus.OVERDUE);
    }

    private boolean estaVencida(Invoice invoice, LocalDate today) {
        return invoice.getStatus() == InvoiceStatus.OVERDUE
                || (invoice.getStatus() == InvoiceStatus.PENDING && invoice.getDueDate().isBefore(today));
    }
}
