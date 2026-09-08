package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.PriceBreakdownLineResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownPaymentResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.entity.BillableEnrollment;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustment;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final PriceRevisionService priceRevisionService;

    public InvoiceQueryService(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                ProgramEnrollmentRepository programEnrollmentRepository, PriceRevisionService priceRevisionService) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.priceRevisionService = priceRevisionService;
    }

    public List<Invoice> porCurso(Long courseEnrollmentId) {
        return invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollmentId);
    }

    public List<Invoice> porCarrera(Long programEnrollmentId) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId);
    }

    // billingStanding real (Fase 6.3) para las respuestas de EnrollmentBillingStatusResponse —
    // el enrollment ya fue validado (ownership u otro criterio) por el caller antes de esto.
    public BillableEnrollment courseBillable(Long courseEnrollmentId) {
        return courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
    }

    public BillableEnrollment programBillable(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
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
    @Transactional(readOnly = true)
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
                resolvePriceRevisionDescription(invoice),
                lines, invoice.getAmountDue(), payments, invoice.getRemainingBalance(), invoice.getStatus().name());
    }

    // Best-effort: la Invoice guarda un snapshot del monto (baseAmount), no una FK al
    // PriceRevision que lo produjo — se re-resuelve con el mismo criterio usado al generar la
    // cuota (curso/carrera + feeType + dueDate) solo para mostrar su descripción en el desglose
    // (hallazgo de auditoría 2026-09-04: el campo faltaba por completo).
    private String resolvePriceRevisionDescription(Invoice invoice) {
        if (invoice.getCourseEnrollment() != null) {
            return priceRevisionService.findRevisionForCourse(
                            invoice.getCourseEnrollment().getCourse().getId(), invoice.getFeeType(), invoice.getDueDate())
                    .map(PriceRevision::getDescription).orElse(null);
        }
        if (invoice.getProgramEnrollment() != null) {
            return priceRevisionService.findRevisionForProgram(
                            invoice.getProgramEnrollment().getProgram().getId(), invoice.getFeeType(), invoice.getDueDate())
                    .map(PriceRevision::getDescription).orElse(null);
        }
        return null;
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
        String humanLabel = adjustment.getReason();
        // El nombre de la promoción solo (adjustment.getReason()) no alcanza para diferenciar una
        // cuota de un plan que después se cambió — la spec pide una referencia legible al plan que
        // habilitó el beneficio, ya que PromotionRedemption está atada a ESA selección puntual.
        if (adjustment.getType() == InvoiceAdjustmentType.PROMOTION) {
            humanLabel = humanLabel + " — " + describeEnablingSelection(adjustment.getInvoice().getPaymentPlanSelection());
        }
        return new PriceBreakdownLineResponse(
                adjustment.getType().name(), adjustment.signedAmount(), humanLabel, source, adjustment.getAppliedAt());
    }

    private String describeEnablingSelection(PaymentPlanSelection selection) {
        return "plan \"" + selection.getPaymentPlanOption().getDescription() + "\" elegido " + selection.getEffectiveFrom();
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
