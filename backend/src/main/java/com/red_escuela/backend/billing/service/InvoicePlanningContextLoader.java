package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.configuration.service.YearCycleFactory;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.domain.YearCycles;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InvoicePlanningContextLoader {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final TermRepository termRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentPlanSelectionRepository selectionRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final YearCycleFactory cycleFactory;
    private final BillingConfigurationReader configurationReader;

    public InvoicePlanningContextLoader(ProgramYearBillingTemplateRepository templateRepository, TermRepository termRepository,
                                       InvoiceRepository invoiceRepository, PaymentPlanSelectionRepository selectionRepository,
                                       ProgramEnrollmentService programEnrollmentService,
                                       ProgramEnrollmentRepository programEnrollmentRepository,
                                       YearCycleFactory cycleFactory, BillingConfigurationReader configurationReader) {
        this.cycleFactory = cycleFactory;
        this.configurationReader = configurationReader;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.templateRepository = templateRepository;
        this.termRepository = termRepository;
        this.invoiceRepository = invoiceRepository;
        this.selectionRepository = selectionRepository;
        this.programEnrollmentService = programEnrollmentService;
    }

    public record PlanningContext(ProgramInvoicePlanningFacts facts, PaymentPlanSelection selection) {
    }

    public PlanningContext load(ProgramEnrollment detached, YearMonth month, LocalDate today) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(detached.getId())
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(detached.getId()));
        Long enrollmentId = enrollment.getId();
        boolean active = enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED || enrollment.getStatus() == ProgramEnrollmentStatus.PAUSED;
        boolean suspended = enrollment.getPaymentStatus() == EnrollmentPaymentStatus.SUSPENDED
                || enrollment.getPaymentStatus() == EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT;
        boolean paused = enrollment.getStatus() == ProgramEnrollmentStatus.PAUSED;
        boolean completed = false;
        boolean onlyFinals = false;
        if (active && !suspended) {
            completed = programEnrollmentService.calculateProgress(enrollmentId).elegibleParaCompletar();
            onlyFinals = programEnrollmentService.hasOnlyFinalExamsLeft(enrollmentId);
        }
        List<PaymentPlanSelection> selections = selectionRepository.findByProgramEnrollmentIdOrderByEffectiveFrom(enrollmentId);
        PaymentPlanSelection selectionAtMonth = selectionRepository.findActiveForProgramEnrollment(enrollmentId, month).orElse(null);
        PaymentPlanCoverage coverage = selectionAtMonth == null ? PaymentPlanCoverage.none()
                : new PaymentPlanCoverage(selectionAtMonth.getPaymentPlanOption().getCadence(), selectionAtMonth.getEffectiveFrom(), selectionAtMonth.getScopeMonths());
        PaymentPlanSelection selection = selectionAtMonth == null || coverage.isAnnualScopeExpiredAt(month) ? null : selectionAtMonth;

        Long hint = enrollment.getCurrentBillingTemplate() != null ? enrollment.getCurrentBillingTemplate().getId() : null;
        List<YearCycle> cycleList = templateRepository.findAllByProgramId(enrollment.getProgram().getId()).stream().map(t -> cycleFactory.cycleOf(t, month.getYear(), today)).toList();
        YearCycles cycles = new YearCycles(cycleList, hint);

        List<Invoice> invoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId);
        List<IssuedEnrollmentFee> fees = invoices.stream().filter(i -> i.getFeeType() == FeeType.ENROLLMENT_FEE)
                .map(i -> new IssuedEnrollmentFee(i.getId(), i.getTemplateId(), i.getEnrollmentFeeSlot(), i.getPeriod(), i.getPaymentStatus(), i.getDueDate(),
                        i.getPrimaryLine().isChargeKeyReleased())).toList();
        Set<YearMonth> recurringPeriods = periods(invoices, FeeType.MONTHLY_INSTALLMENT);

        YearMonth entryMonth = enrollment.getProgramCohort() != null ? YearMonth.from(enrollment.getProgramCohort().getCursadaStart())
                : (enrollment.getEnrollmentDate() != null ? YearMonth.from(enrollment.getEnrollmentDate()) : null);

        var cohort = enrollment.getProgramCohort();
        Long entryTemplateId = null;
        Integer entrySlot = null;
        java.time.LocalDate cursadaStart = null;
        if (cohort != null) {
            entryTemplateId = cohort.getTemplate().getId();
            entrySlot = cycleFactory.cycleOf(cohort.getTemplate(), YearMonth.from(cohort.getCursadaStart()).getYear(), today).slotOf(YearMonth.from(cohort.getCursadaStart()));
            cursadaStart = cohort.getCursadaStart();
        }
        ProgramInvoicePlanningFacts facts = new ProgramInvoicePlanningFacts(enrollmentId, month, today, active, suspended, completed, paused, onlyFinals, entryMonth,
                coverage, !selections.isEmpty(),
                cycles, fees, recurringPeriods, windowsOf(enrollment.getProgram().getId(), cycleList, today),
                entryTemplateId, entrySlot, cursadaStart);
        return new PlanningContext(facts, selection != null ? selection : (selectionAtMonth == null ? latest(selections) : null));
    }

    private PaymentPlanSelection latest(List<PaymentPlanSelection> selections) {
        return selections.isEmpty() ? null : selections.get(selections.size() - 1);
    }

    private Set<YearMonth> periods(List<Invoice> invoices, FeeType type) {
        return invoices.stream().filter(i -> i.getFeeType() == type && !i.getPrimaryLine().isChargeKeyReleased()).map(Invoice::getPeriod).collect(Collectors.toSet());
    }

    private Map<String, SlotWindow> windowsOf(Long programId, List<YearCycle> cycles, LocalDate today) {
        Map<String, SlotWindow> windows = new HashMap<>();
        if (cycles.isEmpty()) {
            return windows;
        }
        Set<String> irregularTypes = configurationReader.irregularTermTypesEffectiveOn(today).types().stream().map(String::toUpperCase).collect(Collectors.toSet());
        List<Term> terms = termRepository.findTermsOfProgram(programId).stream()
                .filter(t -> t.getEnrollmentStart() != null && t.getStartDate() != null)
                .filter(t -> t.getType() == null || !irregularTypes.contains(t.getType().trim().toUpperCase())).toList();
        for (YearCycle cycle : cycles) {
            for (int slot = 1; slot <= cycle.slotCount(); slot++) {
                final int currentSlot = slot;
                List<Term> inSlot = terms.stream()
                        .filter(t -> cycle.slotContains(currentSlot, YearMonth.from(t.getStartDate()))).toList();
                if (inSlot.isEmpty()) {
                    continue;
                }
                LocalDate opens = inSlot.stream().map(t -> t.getEnrollmentStart().toLocalDate()).min(LocalDate::compareTo).orElseThrow();
                LocalDate closes = inSlot.stream()
                        .map(t -> t.getEnrollmentEnd() != null ? t.getEnrollmentEnd().toLocalDate() : t.getStartDate().toLocalDate())
                        .min(LocalDate::compareTo).orElseThrow();
                windows.put(SlotWindow.key(cycle.templateId(), slot), new SlotWindow(opens, closes));
            }
        }
        return windows;
    }
}
