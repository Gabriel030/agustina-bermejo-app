package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class PaymentPlanSelectionInvoiceIssuingService {
    private final InvoicePlanningContextLoader contextLoader;
    private final ProgramMonthlyInvoicePlanner planner;
    private final PlannedInvoiceIssuingService plannedInvoiceIssuingService;

    public PaymentPlanSelectionInvoiceIssuingService(InvoicePlanningContextLoader contextLoader, ProgramMonthlyInvoicePlanner planner, PlannedInvoiceIssuingService plannedInvoiceIssuingService) {
        this.contextLoader = contextLoader;
        this.planner = planner;
        this.plannedInvoiceIssuingService = plannedInvoiceIssuingService;
    }

    @Transactional
    public void issueInvoicesForNewPaymentPlan(ProgramEnrollment programEnrollment, YearMonth month, LocalDate today) {
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(programEnrollment, month, today);
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.ALL, true);
        plannedInvoiceIssuingService.issuePlannedProgramInvoices(programEnrollment, context.selection(), plan, InvoiceIssuingReason.PAYMENT_PLAN_SELECTION, true);
    }
}
