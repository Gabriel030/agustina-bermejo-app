package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;

public record EligibilityPlanningContext(PlanningSubject subject, boolean academicallyActive, boolean suspendedOrWithdrawnForNonPayment,
                                         boolean studyPlanCompleted, boolean paymentPlanChosen, PaymentPlanCadence effectiveCadence) {

    public static EligibilityPlanningContext ofProgram(ProgramInvoicePlanningFacts facts) {
        return new EligibilityPlanningContext(PlanningSubject.PROGRAM, facts.academicallyActive(), facts.suspendedOrWithdrawn(),
                facts.planCompleted(), facts.hasAnyPaymentPlan(), facts.cadenceAtMonth());
    }

    public static EligibilityPlanningContext ofCourse(CourseInvoicePlanningFacts facts) {
        return new EligibilityPlanningContext(PlanningSubject.COURSE, facts.enrolled(), facts.suspendedOrWithdrawn(), false,
                facts.hasPaymentPlan() && facts.cadence() != null, facts.cadence());
    }
}
