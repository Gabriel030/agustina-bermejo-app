package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = {"ONLY_FINAL_EXAMS", "NOT_REQUIRED_ONLY_FINAL_EXAMS"}, decisions = {"D-016", "D-037", "D-014"})
public final class FinalExamsOnlyExemptionPolicy<C extends ProgramFactsContext> implements InvoicePlanningPolicy<C> {
    private final boolean respectsEnrollmentFeeOptIn;
    private final Set<BillingConsultation> consultations;
    private final String violationCode;

    public FinalExamsOnlyExemptionPolicy(FeeType feeType, Set<BillingConsultation> consultations, String violationCode) {
        this.respectsEnrollmentFeeOptIn = feeType == FeeType.ENROLLMENT_FEE;
        this.consultations = Set.copyOf(consultations);
        this.violationCode = violationCode;
    }

    public String ruleName() {
        return "FinalExamsOnlyExemptionPolicy";
    }

    public int plannerQuestionNumber() {
        return 6;
    }

    public PolicyVerdict evaluateRule(C context) {
        boolean exempt = consultations.contains(context.consultation()) && context.facts().onlyFinalExamsLeft()
                && !(respectsEnrollmentFeeOptIn && context.terms().requireEnrollmentFeeForFinalExamOnlyStudents());
        return exempt ? PolicyVerdict.deny(ruleName(), violationCode, context.notIssuedDetail("solo debe finales")) : PolicyVerdict.allow(ruleName());
    }
}
