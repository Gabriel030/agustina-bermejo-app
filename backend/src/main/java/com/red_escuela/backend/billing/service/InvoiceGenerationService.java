package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.planning.ChargePlan;
import com.red_escuela.backend.billing.planning.ChargeScope;
import com.red_escuela.backend.billing.planning.MonthlyChargePlanner;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class InvoiceGenerationService {
    private final ChargePlanningContextLoader contextLoader;
    private final MonthlyChargePlanner planner;
    private final ChargePlanExecutor planExecutor;

    public InvoiceGenerationService(ChargePlanningContextLoader contextLoader, MonthlyChargePlanner planner, ChargePlanExecutor planExecutor) {
        this.contextLoader = contextLoader;
        this.planner = planner;
        this.planExecutor = planExecutor;
    }

    @Transactional
    public void generateChargesForProgram(ProgramEnrollment programEnrollment, YearMonth month, LocalDate today) {
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(programEnrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.ALL, true);
        planExecutor.execute(programEnrollment, context.selection(), plan, IssueReason.PLAN_SELECTION, true);
    }
}
