package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.planning.ChargeKind;
import com.red_escuela.backend.billing.planning.ChargePlan;
import com.red_escuela.backend.billing.planning.PlannedCharge;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChargePlanExecutor {
    private static final Logger log = LoggerFactory.getLogger(ChargePlanExecutor.class);
    private final InvoiceFactory invoiceFactory;

    public ChargePlanExecutor(InvoiceFactory invoiceFactory) {
        this.invoiceFactory = invoiceFactory;
    }

    public int execute(ProgramEnrollment enrollment, PaymentPlanSelection selection, ChargePlan plan) {
        log.info("Plan de cargos: inscripcion={}, mes={}, cargos={}, decisiones={}",
                plan.enrollmentId(), plan.month(), plan.charges().size(), plan.describe());
        int issued = 0;
        for (PlannedCharge charge : plan.charges()) {
            if (issue(enrollment, selection, charge) != null) {
                issued++;
            }
        }
        return issued;
    }

    public Invoice issue(ProgramEnrollment enrollment, PaymentPlanSelection selection, PlannedCharge charge) {
        FeeType feeType = charge.kind() == ChargeKind.ENROLLMENT_FEE ? FeeType.ENROLLMENT_FEE : FeeType.RECURRING_FEE;
        return invoiceFactory.createForProgram(enrollment, selection, feeType, charge.period(), charge.dueDate(), charge.priceDate());
    }
}
