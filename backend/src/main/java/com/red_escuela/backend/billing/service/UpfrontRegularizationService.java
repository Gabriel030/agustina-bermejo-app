package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class UpfrontRegularizationService {
    private static final Logger log = LoggerFactory.getLogger(UpfrontRegularizationService.class);
    private final PaymentPlanSelectionRepository selectionRepository;
    private final InvoicePlanningContextLoader contextLoader;
    private final ProgramMonthlyInvoicePlanner planner;
    private final PlannedInvoiceIssuingService executor;
    private final Clock clock;

    public UpfrontRegularizationService(PaymentPlanSelectionRepository selectionRepository, InvoicePlanningContextLoader contextLoader,
                                        ProgramMonthlyInvoicePlanner planner, PlannedInvoiceIssuingService executor, Clock clock) {
        this.selectionRepository = selectionRepository;
        this.contextLoader = contextLoader;
        this.planner = planner;
        this.executor = executor;
        this.clock = clock;
    }

    public record Candidate(Long selectionId, Long enrollmentId, YearMonth effectiveFrom, Integer scopeMonths, int plannedCharges, int issuedCharges) {
    }

    @Transactional(readOnly = true)
    public List<Candidate> dryRun() {
        return process(false);
    }

    @Transactional
    public List<Candidate> regularize() {
        return process(true);
    }

    private List<Candidate> process(boolean issue) {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        List<Candidate> result = new ArrayList<>();
        for (PaymentPlanSelection selection : selectionRepository.findUpfrontProgramSelectionsWithoutInstallments()) {
            ProgramEnrollment enrollment = selection.getProgramEnrollment();
            InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
            MonthlyInvoicePlan plan = planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.MONTHLY_INSTALLMENT_ONLY, true);
            int issued = issue ? executor.issuePlannedProgramInvoices(enrollment, context.selection(), plan, InvoiceIssuingReason.UPFRONT, true) : 0;
            log.info("Regularizacion UPFRONT: seleccion={}, inscripcion={}, planificadas={}, emitidas={}, simulacion={}",
                    selection.getId(), enrollment.getId(), plan.charges().size(), issued, !issue);
            result.add(new Candidate(selection.getId(), enrollment.getId(), selection.getEffectiveFrom(), selection.getScopeMonths(), plan.charges().size(), issued));
        }
        return result;
    }
}
