package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_SLOT_REQUIRED", decisions = {"D-002", "D-009"})
public final class EnrollmentFeeSlotRequiredPolicy implements Policy<InvoiceIssuingContext> {

    @Override
    public String ruleName() {
        return "EnrollmentFeeSlotRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        boolean programEnrollmentFee = request.feeType() == FeeType.ENROLLMENT_FEE && request.belongsToProgram();
        if (programEnrollmentFee && (request.templateId() == null || request.enrollmentFeeSlot() == null)) {
            return PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_SLOT_REQUIRED", "La matrícula de carrera necesita plantilla y matrícula del ciclo");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
