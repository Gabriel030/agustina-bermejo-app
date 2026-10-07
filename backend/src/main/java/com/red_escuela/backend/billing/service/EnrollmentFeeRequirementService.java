package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.shared.domain.Money;
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

    public enum Status { NOT_ISSUED, PENDING, IN_REVIEW, PARTIAL, PAID, WAIVED, EXEMPTED, CANCELLED }

    public record Obligation(Long templateId, int slot, int slotCount, YearMonth periodFrom, YearMonth periodTo, Status status, Long invoiceId,
                             LocalDate dueDate, Money remainingBalance, boolean satisfied) {
    }

    private final InvoicePlanningContextLoader contextLoader;
    private final InvoiceRepository invoiceRepository;
    private final BankTransferReceiptRepository receiptRepository;
    private final EnrollmentFeeExemptionRepository exemptionRepository;
    private final PlannedInvoiceIssuingService plannedInvoiceIssuingService;
    private final ProgramMonthlyInvoicePlanner planner;
    private final Clock clock;

    public EnrollmentFeeRequirementService(InvoicePlanningContextLoader contextLoader, InvoiceRepository invoiceRepository,
                                           BankTransferReceiptRepository receiptRepository, EnrollmentFeeExemptionRepository exemptionRepository,
                                           PlannedInvoiceIssuingService plannedInvoiceIssuingService, ProgramMonthlyInvoicePlanner planner, Clock clock) {
        this.contextLoader = contextLoader;
        this.invoiceRepository = invoiceRepository;
        this.receiptRepository = receiptRepository;
        this.exemptionRepository = exemptionRepository;
        this.plannedInvoiceIssuingService = plannedInvoiceIssuingService;
        this.planner = planner;
        this.clock = clock;
    }

    @Transactional
    public void requireForTerm(ProgramEnrollment enrollment, Term term) {
        requireForTerm(enrollment, term, Scope.SUBJECT_ENROLLMENT);
    }

    @Transactional
    public void requireForTerm(ProgramEnrollment enrollment, Term term, Scope scope) {
        LocalDate today = LocalDate.now(clock);
        YearMonth reference = term.getStartDate() != null ? YearMonth.from(term.getStartDate())
                : (term.getEnrollmentStart() != null ? YearMonth.from(term.getEnrollmentStart()) : YearMonth.from(today));
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        Optional<YearCycle> cycle = context.facts().cycles().covering(reference);
        if (cycle.isEmpty()) {
            return;
        }
        check(enrollment, context, cycle.get(), cycle.get().slotOf(reference), scope);
    }

    @Transactional
    public void requireForCurrentPeriod(ProgramEnrollment enrollment, Scope scope) {
        LocalDate today = LocalDate.now(clock);
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        ProgramInvoicePlanningFacts facts = context.facts();
        if (scope == Scope.CERTIFICATE && (!facts.academicallyActive() || facts.planCompleted())) {
            return;
        }
        YearCycle latest = null;
        int latestSlot = 0;
        for (YearCycle cycle : facts.cycles().all()) {
            for (int slot = 1; slot <= cycle.slotCount(); slot++) {
                if (planner.isDueNow(facts, cycle, slot, true) && (latest == null || cycle.start().isAfter(latest.start())
                        || (cycle.start().equals(latest.start()) && slot > latestSlot))) {
                    latest = cycle;
                    latestSlot = slot;
                }
            }
        }
        if (latest != null) {
            check(enrollment, context, latest, latestSlot, scope);
        }
    }

    @Transactional(readOnly = true)
    public List<Obligation> statusOf(ProgramEnrollment enrollment) {
        LocalDate today = LocalDate.now(clock);
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        ProgramInvoicePlanningFacts facts = context.facts();
        List<YearCycle> relevant = new ArrayList<>();
        facts.cycles().latestStartedOn(YearMonth.from(today)).ifPresent(relevant::add);
        facts.cycles().all().stream().filter(c -> c.start().isAfter(YearMonth.from(today))).findFirst().ifPresent(relevant::add);
        List<Obligation> result = new ArrayList<>();
        for (YearCycle cycle : relevant) {
            for (int slot = 1; slot <= cycle.slotCount(); slot++) {
                if (facts.entryMonth() != null && cycle.slotTo(slot).isBefore(facts.entryMonth())) {
                    continue;
                }
                result.add(describe(enrollment, facts, cycle, slot));
            }
        }
        return result;
    }

    private void check(ProgramEnrollment enrollment, InvoicePlanningContextLoader.PlanningContext context, YearCycle cycle, int slot, Scope scope) {
        ProgramInvoicePlanningFacts facts = context.facts();
        ProgramYearBillingTemplate template = cycle.template();
        boolean blocks = switch (scope) {
            case SUBJECT_ENROLLMENT -> template.isBlocksEnrollment();
            case EXAM_REGISTRATION -> template.isBlocksExamRegistration();
            case CERTIFICATE -> template.isBlocksCertificates();
        };
        if (!blocks) {
            return;
        }
        boolean finalsOnlyExempt = (scope == Scope.EXAM_REGISTRATION || scope == Scope.CERTIFICATE)
                && facts.onlyFinalExamsLeft() && !template.isRequireEnrollmentFeeForFinalExamOnlyStudents();
        if (finalsOnlyExempt) {
            return;
        }
        if (facts.entryMonth() != null && cycle.slotTo(slot).isBefore(facts.entryMonth())) {
            return;
        }
        if (exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(enrollment.getId(), cycle.templateId(), slot).isPresent()) {
            return;
        }
        Optional<IssuedEnrollmentFee> existing = facts.feeOf(cycle, slot);
        if (existing.isPresent() && existing.get().isSettled()) {
            return;
        }
        if (existing.isEmpty() && !issueOnDemand(enrollment, context, cycle, slot)) {
            return;
        }
        throw new EnrollmentFeeNotPaidForTermException(enrollment.getId(), cycle.templateId(), slot);
    }

    private boolean issueOnDemand(ProgramEnrollment enrollment, InvoicePlanningContextLoader.PlanningContext context, YearCycle cycle, int slot) {
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.ENROLLMENT_FEE_ONLY, true);
        Optional<PlannedInvoice> charge = plan.charges().stream()
                .filter(c -> cycle.templateId().equals(c.templateId()) && c.slot() != null && c.slot() == slot).findFirst();
        if (charge.isEmpty()) {
            return false;
        }
        log.info("Matrícula emitida a demanda: inscripcion={}, plantilla={}, matricula={}, periodo={}",
                enrollment.getId(), cycle.templateId(), slot, charge.get().period());
        plannedInvoiceIssuingService.issuePlannedProgramInvoice(enrollment, context.selection(), charge.get(), com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND, false);
        return true;
    }

    private Obligation describe(ProgramEnrollment enrollment, ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot) {
        boolean exempted = exemptionRepository
                .findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(enrollment.getId(), cycle.templateId(), slot).isPresent();
        Invoice invoice = facts.feeOf(cycle, slot).flatMap(f -> invoiceRepository.findById(f.invoiceId())).orElse(null);
        Status status;
        if (invoice == null) {
            status = exempted ? Status.EXEMPTED : Status.NOT_ISSUED;
        } else if (invoice.getStatus() == InvoiceStatus.PAID) {
            status = Status.PAID;
        } else if (invoice.getStatus() == InvoiceStatus.WAIVED) {
            status = Status.WAIVED;
        } else if (exempted) {
            status = Status.EXEMPTED;
        } else if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            status = Status.CANCELLED;
        } else if (receiptRepository.existsByInvoiceIdAndStatus(invoice.getId(), BankTransferReceiptStatus.PENDING_REVIEW)) {
            status = Status.IN_REVIEW;
        } else if (invoice.getRemainingBalance().isGreaterThan(invoice.getAmountDue()) || invoice.getRemainingBalance().equals(invoice.getAmountDue())) {
            status = Status.PENDING;
        } else {
            status = Status.PARTIAL;
        }
        boolean satisfied = status == Status.PAID || status == Status.WAIVED || status == Status.EXEMPTED;
        return new Obligation(cycle.templateId(), slot, cycle.slotCount(), cycle.slotFrom(slot), cycle.slotTo(slot), status,
                invoice != null ? invoice.getId() : null, invoice != null ? invoice.getDueDate() : null,
                invoice != null && !satisfied ? invoice.getRemainingBalance() : null, satisfied);
    }
}
