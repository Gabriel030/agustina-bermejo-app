package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_SLOT_REQUIRED", decisions = {"D-002", "D-009"})
public final class EnrollmentFeeSlotRequiredPolicy implements Policy<InvoiceIssuingContext> {
    public String ruleName() {
        return "EnrollmentFeeSlotRequiredPolicy";
    }

    public PolicyVerdict evaluateRule(InvoiceIssuingContext ctx) {
        boolean programFee = ctx.request().feeType() == FeeType.ENROLLMENT_FEE
                && (ctx.request().target() instanceof InvoiceRecipient.ProgramEnrollmentRecipient || ctx.request().target() instanceof InvoiceRecipient.ProgramApplicantRecipient);
        if (programFee && (ctx.request().templateId() == null || ctx.request().slot() == null)) {
            return PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_SLOT_REQUIRED", "La matrícula de carrera necesita plantilla y matrícula del ciclo");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
