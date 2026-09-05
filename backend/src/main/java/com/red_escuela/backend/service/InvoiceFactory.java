package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.PaymentPlanSelection;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Scholarship;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ScholarshipRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// Builds a new Invoice. Idempotent by design: the DB-level unique constraint on
// (billable, feeType, period) is the real guard against duplicates under concurrency — this
// factory just turns the resulting DataIntegrityViolationException into a silent no-op instead
// of letting it reach the caller as an error.
//
// crearParaCurso/crearParaCarrera use REQUIRES_NEW (not the default REQUIRED): a saveAndFlush
// that violates the unique constraint leaves Hibernate's persistence context unusable for the
// rest of whatever transaction it ran in — catching the exception in Java doesn't undo that.
// InvoiceGenerationService calls these in a loop (one UPFRONT selection can generate several
// months in the same request) and needs a failed one to roll back on its own so the loop can
// keep going for the rest. The ad-hoc methods below stay REQUIRED on purpose: nothing calls them
// in a loop expecting to skip a duplicate and continue — a failure there should fail the whole
// request, same as any other write.
@Service
public class InvoiceFactory {

    private final PriceRevisionService priceRevisionService;
    private final InvoiceRepository invoiceRepository;
    private final ScholarshipRepository scholarshipRepository;

    public InvoiceFactory(PriceRevisionService priceRevisionService, InvoiceRepository invoiceRepository,
                           ScholarshipRepository scholarshipRepository) {
        this.priceRevisionService = priceRevisionService;
        this.invoiceRepository = invoiceRepository;
        this.scholarshipRepository = scholarshipRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Invoice crearParaCurso(CourseEnrollment courseEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        return saveIdempotently(construirParaCurso(courseEnrollment, selection, feeType, period, dueDate));
    }

    private Invoice construirParaCurso(CourseEnrollment courseEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForCourse(courseEnrollment.getCourse().getId(), feeType, dueDate);
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(courseEnrollment);
        invoice.setPaymentPlanSelection(selection);
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(price);
        invoice.setStatus(InvoiceStatus.PENDING);
        applyActiveScholarshipIfPresent(invoice, scholarshipRepository.findByCourseEnrollmentId(courseEnrollment.getId()), period);
        return invoice;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Invoice crearParaCarrera(ProgramEnrollment programEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        return saveIdempotently(construirParaCarrera(programEnrollment, selection, feeType, period, dueDate));
    }

    // Variante REQUIRED (no REQUIRES_NEW), para el único caller que necesita la Invoice atada a
    // SU MISMA transacción en vez de aislada: ProgramEnrollmentBillingScheduler.generarMatricula
    // crea una única Invoice de matrícula como parte de una operación más grande (seleccionar
    // plan de pago) — si algo después falla (ej. aplicar el descuento de segunda cohorte), toda
    // la operación tiene que revertir junta, no dejar la Invoice de matrícula huérfana ya
    // commiteada. Mismo criterio que los métodos crearAdHoc* de abajo: "una falla acá revierte
    // todo el request, no hay loop que necesite saltear un duplicado y seguir".
    @Transactional
    public Invoice crearParaCarreraAtomica(ProgramEnrollment programEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        return invoiceRepository.saveAndFlush(construirParaCarrera(programEnrollment, selection, feeType, period, dueDate));
    }

    private Invoice construirParaCarrera(ProgramEnrollment programEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForProgram(programEnrollment.getProgram().getId(), feeType, dueDate);
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(programEnrollment);
        invoice.setPaymentPlanSelection(selection);
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(price);
        invoice.setStatus(InvoiceStatus.PENDING);
        applyActiveScholarshipIfPresent(invoice, scholarshipRepository.findByProgramEnrollmentId(programEnrollment.getId()), period);
        return invoice;
    }

    private void applyActiveScholarshipIfPresent(Invoice invoice, List<Scholarship> scholarships, YearMonth period) {
        scholarships.stream()
                .filter(scholarship -> scholarship.isActiveFor(period))
                .findFirst()
                .ifPresent(scholarship -> invoice.applyAdjustment(InvoiceAdjustmentType.SCHOLARSHIP, scholarship.getValue(),
                        scholarship.getReason(), null));
    }

    // Ad-hoc, not yet linked to any enrollment — used only by the Épica 7 Fase 3 self-service
    // application flow, where the fee is paid *before* the enrollment itself exists (the
    // enrollment is only created once staff approves the application). The caller is
    // responsible for setting courseEnrollment/programEnrollment once it exists (see
    // ProgramEnrollmentApplicationService.aprobar/CourseEnrollmentApplicationService) — until
    // then this invoice has none of the three set, the one legitimate case the CHECK constraint
    // allows besides the ad-hoc subject/exam-board invoices of Fase 6.7.
    @Transactional
    public Invoice crearAdHocParaCarrera(Program program, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForProgram(program.getId(), feeType, dueDate);
        return saveAdHoc(feeType, period, dueDate, price);
    }

    @Transactional
    public Invoice crearAdHocParaCurso(com.red_escuela.backend.entity.Course course, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForCourse(course.getId(), feeType, dueDate);
        return saveAdHoc(feeType, period, dueDate, price);
    }

    private Invoice saveAdHoc(FeeType feeType, YearMonth period, LocalDate dueDate, java.math.BigDecimal price) {
        Invoice invoice = new Invoice();
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(price);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.saveAndFlush(invoice);
    }

    private Invoice saveIdempotently(Invoice invoice) {
        try {
            return invoiceRepository.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException e) {
            // Already exists for this billable+feeType+period — idempotent no-op, not an error.
            // Postgres aborts the whole physical transaction on a failed statement — catching the
            // exception here doesn't undo that, a later commit attempt on this same (REQUIRES_NEW)
            // transaction would still fail with UnexpectedRollbackException. Marking it
            // rollback-only tells Spring to roll back instead of trying to commit an aborted
            // transaction — this method's caller (a loop in InvoiceGenerationService) then
            // continues in its OWN transaction, unaffected.
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();
            return null;
        }
    }
}
