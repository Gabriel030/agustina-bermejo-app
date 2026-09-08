package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.entity.ScholarshipStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.billing.exception.ScholarshipExceedsCohortSpanException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.red_escuela.backend.service.CurrentPersonService;
import com.red_escuela.backend.service.RecurringFeeWindowService;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Service
public class ScholarshipService {

    private final ScholarshipRepository scholarshipRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final CurrentPersonService currentPersonService;
    private final InvoiceRepository invoiceRepository;
    private final CreditNoteService creditNoteService;

    public ScholarshipService(ScholarshipRepository scholarshipRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                               ProgramEnrollmentRepository programEnrollmentRepository, RecurringFeeWindowService recurringFeeWindowService,
                               CurrentPersonService currentPersonService, InvoiceRepository invoiceRepository,
                               CreditNoteService creditNoteService) {
        this.scholarshipRepository = scholarshipRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.currentPersonService = currentPersonService;
        this.invoiceRepository = invoiceRepository;
        this.creditNoteService = creditNoteService;
    }

    @Transactional
    public Scholarship grantForCourse(Long courseEnrollmentId, CreateScholarshipRequest request) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        validateInstallmentsWithinCohortSpan(request.installments(), recurringFeeWindowService.remainingInvoices(enrollment));
        Scholarship scholarship = build(request, currentPersonService.resolveCurrentPerson());
        scholarship.setCourseEnrollment(enrollment);
        scholarship = scholarshipRepository.save(scholarship);
        applyRetroactiveCreditIfCurrentInvoiceAlreadyPaid(scholarship,
                invoiceRepository.findByCourseEnrollmentIdAndStatusIn(courseEnrollmentId, List.of(InvoiceStatus.PAID)),
                enrollment.getStudent());
        return scholarship;
    }

    @Transactional
    public Scholarship grantForProgram(Long programEnrollmentId, CreateScholarshipRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        validateInstallmentsWithinCohortSpan(request.installments(), recurringFeeWindowService.remainingInvoices(enrollment));
        Scholarship scholarship = build(request, currentPersonService.resolveCurrentPerson());
        scholarship.setProgramEnrollment(enrollment);
        scholarship = scholarshipRepository.save(scholarship);
        applyRetroactiveCreditIfCurrentInvoiceAlreadyPaid(scholarship,
                invoiceRepository.findByProgramEnrollmentIdAndStatusIn(programEnrollmentId, List.of(InvoiceStatus.PAID)),
                enrollment.getStudent());
        return scholarship;
    }

    // Una beca otorgada después de que ya se generó y pagó la cuota del período corriente no
    // puede recuperar ese descuento vía Scholarship.consumeOne (esa Invoice ya está PAID, no se
    // reabre) — en vez de eso se consume igual la primera installment (mismo criterio de conteo
    // que si el descuento se hubiera aplicado en el momento) y la diferencia se devuelve como
    // CreditNote en lugar de un ajuste sobre la Invoice.
    private void applyRetroactiveCreditIfCurrentInvoiceAlreadyPaid(Scholarship scholarship, List<Invoice> paidInvoices, Student student) {
        YearMonth currentPeriod = YearMonth.now();
        paidInvoices.stream()
                .filter(invoice -> invoice.getFeeType() == FeeType.RECURRING_FEE && currentPeriod.equals(invoice.getPeriod()))
                .findFirst()
                .ifPresent(invoice -> {
                    scholarship.consumeOne(invoice);
                    scholarshipRepository.save(scholarship);
                    // La installment se consume igual (arriba) aunque el descuento resuelva a $0 —
                    // cuenta como "ya aplicada" a todos los efectos de la beca — pero solo si hay
                    // plata real de por medio nace una CreditNote.
                    BigDecimal creditAmount = scholarship.getValue().resolve(invoice.getBaseAmount());
                    if (creditAmount.signum() > 0) {
                        var creditNote = creditNoteService.createFor(student, creditAmount, CreditNoteReasonType.RETROACTIVE_SCHOLARSHIP,
                                scholarship.getReason(), scholarship.getGrantedBy());
                        creditNote.getSourceInvoices().add(invoice);
                    }
                });
    }

    @Transactional
    public void cancelActiveForCourse(Long courseEnrollmentId, String reason) {
        scholarshipRepository.findByCourseEnrollmentIdAndStatus(courseEnrollmentId, ScholarshipStatus.ACTIVE)
                .ifPresent(scholarship -> {
                    scholarship.cancelRemaining(reason);
                    scholarshipRepository.save(scholarship);
                });
    }

    @Transactional
    public void cancelActiveForProgram(Long programEnrollmentId, String reason) {
        scholarshipRepository.findByProgramEnrollmentIdAndStatus(programEnrollmentId, ScholarshipStatus.ACTIVE)
                .ifPresent(scholarship -> {
                    scholarship.cancelRemaining(reason);
                    scholarshipRepository.save(scholarship);
                });
    }

    private Scholarship build(CreateScholarshipRequest request, Person actor) {
        Scholarship scholarship = new Scholarship();
        scholarship.setValue(request.value().toValue());
        scholarship.setReason(request.reason());
        scholarship.setGrantedBy(actor);
        scholarship.addInstallments(request.installments());
        return scholarship;
    }

    private void validateInstallmentsWithinCohortSpan(int installments, Integer maxInstallments) {
        if (maxInstallments != null && installments > maxInstallments) {
            throw new ScholarshipExceedsCohortSpanException(installments, maxInstallments);
        }
    }
}
