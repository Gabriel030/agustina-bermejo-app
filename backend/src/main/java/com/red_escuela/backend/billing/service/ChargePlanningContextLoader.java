package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.planning.ChargePlanningFacts;
import com.red_escuela.backend.billing.planning.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.SlotWindow;
import com.red_escuela.backend.billing.planning.YearCycle;
import com.red_escuela.backend.billing.planning.YearCycles;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
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
public class ChargePlanningContextLoader {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final TermRepository termRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentPlanSelectionRepository selectionRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final Set<String> irregularTermTypes;

    public ChargePlanningContextLoader(ProgramYearBillingTemplateRepository templateRepository, TermRepository termRepository,
                                       InvoiceRepository invoiceRepository, PaymentPlanSelectionRepository selectionRepository,
                                       ProgramEnrollmentService programEnrollmentService,
                                       ProgramEnrollmentRepository programEnrollmentRepository,
                                       @Value("${app.billing.irregular-term-types:INTENSIVO,VERANO,INVIERNO,RECUPERATORIO,EXTRAORDINARIO}") Set<String> irregularTermTypes) {
        this.irregularTermTypes = irregularTermTypes.stream().map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.templateRepository = templateRepository;
        this.termRepository = termRepository;
        this.invoiceRepository = invoiceRepository;
        this.selectionRepository = selectionRepository;
        this.programEnrollmentService = programEnrollmentService;
    }

    public record PlanningContext(ChargePlanningFacts facts, PaymentPlanSelection selection) {
    }

    public PlanningContext load(ProgramEnrollment detached, YearMonth month, LocalDate today) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(detached.getId())
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(detached.getId()));
        Long enrollmentId = enrollment.getId();
        boolean active = enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED || enrollment.getStatus() == ProgramEnrollmentStatus.PAUSED;
        boolean suspended = enrollment.getBillingStanding() == EnrollmentBillingStanding.SUSPENDED
                || enrollment.getBillingStanding() == EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT;
        boolean paused = enrollment.getStatus() == ProgramEnrollmentStatus.PAUSED;
        boolean completed = false;
        boolean onlyFinals = false;
        if (active && !suspended) {
            completed = programEnrollmentService.calculateProgress(enrollmentId).elegibleParaCompletar();
            onlyFinals = programEnrollmentService.hasOnlyFinalExamsLeft(enrollmentId);
        }
        List<PaymentPlanSelection> selections = selectionRepository.findByProgramEnrollmentIdOrderByEffectiveFrom(enrollmentId);
        PaymentPlanSelection selectionAtMonth = selectionRepository.findActiveForProgramEnrollment(enrollmentId, month).orElse(null);
        boolean upfrontExpired = selectionAtMonth != null && selectionAtMonth.getPaymentPlanOption().getCadence() == PaymentPlanCadence.UPFRONT
                && selectionAtMonth.getScopeMonths() != null
                && month.isAfter(selectionAtMonth.getEffectiveFrom().plusMonths(selectionAtMonth.getScopeMonths() - 1L));
        PaymentPlanSelection selection = selectionAtMonth == null || upfrontExpired ? null : selectionAtMonth;
        PaymentPlanCadence cadence = selection != null ? selection.getPaymentPlanOption().getCadence()
                : (upfrontExpired ? PaymentPlanCadence.MONTHLY : null);

        Long hint = enrollment.getCurrentBillingTemplate() != null ? enrollment.getCurrentBillingTemplate().getId() : null;
        List<YearCycle> cycleList = templateRepository.findAllByProgramId(enrollment.getProgram().getId()).stream().map(t -> YearCycle.of(t, month.getYear())).toList();
        YearCycles cycles = new YearCycles(cycleList, hint);

        List<Invoice> invoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId);
        List<IssuedEnrollmentFee> fees = invoices.stream().filter(i -> i.getFeeType() == FeeType.ENROLLMENT_FEE)
                .map(i -> new IssuedEnrollmentFee(i.getId(), i.getTemplateId(), i.getEnrollmentFeeSlot(), i.getPeriod(), i.getStatus(), i.getDueDate())).toList();
        Set<YearMonth> recurringPeriods = periods(invoices, FeeType.RECURRING_FEE);

        YearMonth entryMonth = enrollment.getProgramCohort() != null ? YearMonth.from(enrollment.getProgramCohort().getCursadaStart())
                : (enrollment.getEnrollmentDate() != null ? YearMonth.from(enrollment.getEnrollmentDate()) : null);

        ChargePlanningFacts facts = new ChargePlanningFacts(enrollmentId, month, today, active, suspended, completed, paused, onlyFinals, entryMonth,
                cadence, !selections.isEmpty(),
                cycles, fees, recurringPeriods, windowsOf(enrollment.getProgram().getId(), cycleList));
        return new PlanningContext(facts, selection != null ? selection : (selectionAtMonth == null ? latest(selections) : null));
    }

    private PaymentPlanSelection latest(List<PaymentPlanSelection> selections) {
        return selections.isEmpty() ? null : selections.get(selections.size() - 1);
    }

    private Set<YearMonth> periods(List<Invoice> invoices, FeeType type) {
        return invoices.stream().filter(i -> i.getFeeType() == type).map(Invoice::getPeriod).collect(Collectors.toSet());
    }

    private Map<String, SlotWindow> windowsOf(Long programId, List<YearCycle> cycles) {
        Map<String, SlotWindow> windows = new HashMap<>();
        if (cycles.isEmpty()) {
            return windows;
        }
        List<Term> terms = termRepository.findTermsOfProgram(programId).stream()
                .filter(t -> t.getEnrollmentStart() != null && t.getStartDate() != null)
                .filter(t -> t.getType() == null || !irregularTermTypes.contains(t.getType().trim().toUpperCase())).toList();
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
