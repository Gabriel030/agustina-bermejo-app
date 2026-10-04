package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.issuing.ChargeIssuer;
import com.red_escuela.backend.billing.issuing.ChargeRequest;
import com.red_escuela.backend.billing.issuing.ChargeTarget;
import com.red_escuela.backend.billing.issuing.IssueResult;
import com.red_escuela.backend.billing.planning.ChargeKind;
import com.red_escuela.backend.billing.planning.ChargePlan;
import com.red_escuela.backend.billing.planning.PlannedCharge;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChargePlanExecutor {
    private static final Logger log = LoggerFactory.getLogger(ChargePlanExecutor.class);
    private final ChargeIssuer chargeIssuer;

    public ChargePlanExecutor(ChargeIssuer chargeIssuer) {
        this.chargeIssuer = chargeIssuer;
    }

    public int execute(ProgramEnrollment enrollment, PaymentPlanSelection selection, ChargePlan plan, IssueReason reason) {
        return execute(enrollment, selection, plan, reason, false);
    }

    public int execute(ProgramEnrollment enrollment, PaymentPlanSelection selection, ChargePlan plan, IssueReason reason, boolean inCurrentTransaction) {
        log.info("Plan de cargos: inscripcion={}, mes={}, cargos={}, motivo={}, decisiones={}",
                plan.enrollmentId(), plan.month(), plan.charges().size(), reason, plan.describe());
        int issued = 0;
        for (PlannedCharge charge : plan.charges()) {
            if (issue(enrollment, selection, charge, reason, inCurrentTransaction) instanceof IssueResult.Issued) {
                issued++;
            }
        }
        return issued;
    }

    public IssueResult issue(ProgramEnrollment enrollment, PaymentPlanSelection selection, PlannedCharge charge, IssueReason reason, boolean inCurrentTransaction) {
        FeeType feeType = charge.kind() == ChargeKind.ENROLLMENT_FEE ? FeeType.ENROLLMENT_FEE : FeeType.RECURRING_FEE;
        IssueReason effectiveReason = charge.upfront() ? IssueReason.UPFRONT : reason;
        ChargeRequest request = new ChargeRequest(new ChargeTarget.ProgramStudent(enrollment), feeType, charge.period(), charge.dueDate(), effectiveReason,
                selection, charge.templateId(), charge.slot(), charge.entry(), null, null, null, null);
        IssueResult result = inCurrentTransaction ? chargeIssuer.issueInCurrentTransaction(request) : chargeIssuer.issueInOwnTransaction(request);
        if (result instanceof IssueResult.Rejected rejected) {
            throw new ChargeRejectedException(rejected.evaluation());
        }
        return result;
    }

    public int executeForCourse(CourseEnrollment enrollment, PaymentPlanSelection selection, ChargePlan plan) {
        log.info("Plan de cargos de curso: inscripcion={}, mes={}, cargos={}, decisiones={}", plan.enrollmentId(), plan.month(), plan.charges().size(), plan.describe());
        int issued = 0;
        for (PlannedCharge charge : plan.charges()) {
            IssueReason reason = charge.upfront() ? IssueReason.UPFRONT : IssueReason.COURSE_MONTHLY;
            ChargeRequest request = new ChargeRequest(new ChargeTarget.CourseStudent(enrollment), FeeType.RECURRING_FEE, charge.period(), charge.dueDate(),
                    reason, selection, null, null, false, null, null, null, null);
            IssueResult result = chargeIssuer.issueInOwnTransaction(request);
            if (result instanceof IssueResult.Rejected rejected) {
                throw new ChargeRejectedException(rejected.evaluation());
            }
            if (result instanceof IssueResult.Issued) {
                issued++;
            }
        }
        return issued;
    }
}
