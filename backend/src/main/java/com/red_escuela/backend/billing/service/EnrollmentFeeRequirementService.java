package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.ChargePlanningFacts;
import com.red_escuela.backend.billing.planning.MonthlyChargePlanner;
import com.red_escuela.backend.billing.planning.PlannedCharge;
import com.red_escuela.backend.billing.planning.SlotWindow;
import com.red_escuela.backend.billing.planning.YearCycle;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidForTermException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EnrollmentFeeRequirementService {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentFeeRequirementService.class);

    public enum Scope { SUBJECT_ENROLLMENT, EXAM_REGISTRATION, CERTIFICATE }

    public enum Status { NOT_ISSUED, PENDING, IN_REVIEW, PARTIAL, PAID, WAIVED, EXEMPTED }

    public record Obligation(Long templateId, int slot, YearMonth periodFrom, YearMonth periodTo, Status status, Long invoiceId,
                             LocalDate dueDate, boolean satisfied) {
    }

    private final ChargePlanningContextLoader contextLoader;
    private final InvoiceRepository invoiceRepository;
    private final PaymentReceiptRepository receiptRepository;
    private final EnrollmentFeeExemptionRepository exemptionRepository;
    private final ChargePlanExecutor planExecutor;
    private final Clock clock;

    public EnrollmentFeeRequirementService(ChargePlanningContextLoader contextLoader, InvoiceRepository invoiceRepository,
                                           PaymentReceiptRepository receiptRepository, EnrollmentFeeExemptionRepository exemptionRepository,
                                           ChargePlanExecutor planExecutor, Clock clock) {
        this.contextLoader = contextLoader;
        this.invoiceRepository = invoiceRepository;
        this.receiptRepository = receiptRepository;
        this.exemptionRepository = exemptionRepository;
        this.planExecutor = planExecutor;
        this.clock = clock;
    }

    @Transactional
    public void requireForTerm(ProgramEnrollment enrollment, Term term) {
        LocalDate today = LocalDate.now(clock);
        YearMonth reference = term.getStartDate() != null ? YearMonth.from(term.getStartDate())
                : (term.getEnrollmentStart() != null ? YearMonth.from(term.getEnrollmentStart()) : YearMonth.from(today));
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        Optional<YearCycle> cycle = context.facts().cycles().covering(reference);
        if (cycle.isEmpty()) {
            return;
        }
        check(enrollment, context, cycle.get(), cycle.get().slotOf(reference), Scope.SUBJECT_ENROLLMENT);
    }

    @Transactional
    public void requireForCurrentPeriod(ProgramEnrollment enrollment, Scope scope) {
        LocalDate today = LocalDate.now(clock);
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        Optional<YearCycle> cycle = context.facts().cycles().latestStartedOn(YearMonth.from(today));
        if (cycle.isEmpty()) {
            return;
        }
        for (int slot = cycle.get().slotCount(); slot >= 1; slot--) {
            if (emissionReached(context.facts(), cycle.get(), slot, today)) {
                check(enrollment, context, cycle.get(), slot, scope);
                return;
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Obligation> statusOf(ProgramEnrollment enrollment) {
        LocalDate today = LocalDate.now(clock);
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        ChargePlanningFacts facts = context.facts();
        List<YearCycle> relevant = new ArrayList<>();
        facts.cycles().latestStartedOn(YearMonth.from(today)).ifPresent(relevant::add);
        facts.cycles().all().stream().filter(c -> c.start().isAfter(YearMonth.from(today))).findFirst().ifPresent(relevant::add);
        List<Obligation> result = new ArrayList<>();
        for (YearCycle cycle : relevant) {
            for (int slot = 1; slot <= cycle.slotCount(); slot++) {
                if (facts.entryMonth() != null && cycle.slotTo(slot).isBefore(facts.entryMonth())) {
                    continue;
                }
                result.add(describe(enrollment, cycle, slot));
            }
        }
        return result;
    }

    private void check(ProgramEnrollment enrollment, ChargePlanningContextLoader.PlanningContext context, YearCycle cycle, int slot, Scope scope) {
        ChargePlanningFacts facts = context.facts();
        ProgramYearBillingTemplate template = cycle.template();
        boolean blocks = switch (scope) {
            case SUBJECT_ENROLLMENT -> template.isBlocksEnrollment();
            case EXAM_REGISTRATION -> template.isBlocksExamRegistration();
            case CERTIFICATE -> template.isBlocksCertificates();
        };
        if (!blocks) {
            return;
        }
        if (scope == Scope.EXAM_REGISTRATION && facts.onlyFinalExamsLeft() && !template.isRequireEnrollmentFeeForFinalExamOnlyStudents()) {
            return;
        }
        if (facts.entryMonth() != null && cycle.slotTo(slot).isBefore(facts.entryMonth())) {
            return;
        }
        if (exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(enrollment.getId(), cycle.templateId(), slot).isPresent()) {
            return;
        }
        Optional<Invoice> existing = coveringInvoices(enrollment.getId(), cycle, slot).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID || i.getStatus() == InvoiceStatus.WAIVED).findFirst();
        if (existing.isPresent()) {
            return;
        }
        if (coveringInvoices(enrollment.getId(), cycle, slot).isEmpty() && facts.academicallyActive()) {
            PlannedCharge charge = MonthlyChargePlanner.chargeForSlot(facts, cycle, slot,
                    "matrícula a demanda del ciclo " + cycle.termStart().getYear() + " (matrícula " + slot + ")");
            log.info("Matrícula emitida a demanda: inscripcion={}, plantilla={}, matricula={}, periodo={}",
                    enrollment.getId(), cycle.templateId(), slot, charge.period());
            planExecutor.issue(enrollment, context.selection(), charge);
        }
        throw new EnrollmentFeeNotPaidForTermException(enrollment.getId(), cycle.templateId(), slot);
    }

    private boolean emissionReached(ChargePlanningFacts facts, YearCycle cycle, int slot, LocalDate today) {
        SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
        if (window == null) {
            return !cycle.slotPeriod(slot).atDay(1).isAfter(today);
        }
        return !window.opensOn().minusDays(cycle.template().getEnrollmentFeeLeadDays()).isAfter(today);
    }

    private List<Invoice> coveringInvoices(Long enrollmentId, YearCycle cycle, int slot) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId).stream()
                .filter(i -> i.getFeeType() == FeeType.ENROLLMENT_FEE && i.getStatus() != InvoiceStatus.CANCELLED)
                .filter(i -> cycle.slotContains(slot, i.getPeriod()))
                .toList();
    }

    private Obligation describe(ProgramEnrollment enrollment, YearCycle cycle, int slot) {
        boolean exempted = exemptionRepository
                .findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(enrollment.getId(), cycle.templateId(), slot).isPresent();
        List<Invoice> invoices = coveringInvoices(enrollment.getId(), cycle, slot);
        Invoice paid = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.PAID || i.getStatus() == InvoiceStatus.WAIVED).findFirst().orElse(null);
        Invoice invoice = paid != null ? paid : (invoices.isEmpty() ? null : invoices.get(0));
        Status status;
        if (invoice == null) {
            status = exempted ? Status.EXEMPTED : Status.NOT_ISSUED;
        } else if (invoice.getStatus() == InvoiceStatus.PAID) {
            status = Status.PAID;
        } else if (invoice.getStatus() == InvoiceStatus.WAIVED) {
            status = Status.WAIVED;
        } else if (receiptRepository.existsByInvoiceIdAndStatus(invoice.getId(), PaymentReceiptStatus.PENDING_REVIEW)) {
            status = Status.IN_REVIEW;
        } else if (invoice.getRemainingBalance().isGreaterThan(invoice.getAmountDue()) || invoice.getRemainingBalance().equals(invoice.getAmountDue())) {
            status = exempted ? Status.EXEMPTED : Status.PENDING;
        } else {
            status = exempted ? Status.EXEMPTED : Status.PARTIAL;
        }
        boolean satisfied = status == Status.PAID || status == Status.WAIVED || status == Status.EXEMPTED;
        return new Obligation(cycle.templateId(), slot, cycle.slotFrom(slot), cycle.slotTo(slot), status,
                invoice != null ? invoice.getId() : null, invoice != null ? invoice.getDueDate() : null, satisfied);
    }
}
