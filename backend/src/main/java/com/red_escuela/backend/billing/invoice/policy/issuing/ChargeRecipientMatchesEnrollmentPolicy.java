package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = {"ENROLLMENT_RECIPIENT_MISMATCH", "ENROLLMENT_REQUIRED"}, decisions = {"D-097", "D-009"})
public final class ChargeRecipientMatchesEnrollmentPolicy implements Policy<InvoiceIssuingContext> {

    private static final Set<InvoiceIssuingReason> ENROLLMENT_REASONS = Set.of(InvoiceIssuingReason.MONTHLY_RUN, InvoiceIssuingReason.COURSE_MONTHLY_RUN,
            InvoiceIssuingReason.PAYMENT_PLAN_SELECTION, InvoiceIssuingReason.REACTIVATION, InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND,
            InvoiceIssuingReason.YEAR_CONFIGURATION_CHANGE);

    @Override
    public String ruleName() {
        return "ChargeRecipientMatchesEnrollmentPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        if (request.hasEnrollment() && !context.facts().enrollmentMatchesRecipient()) {
            return PolicyVerdict.deny(ruleName(), "ENROLLMENT_RECIPIENT_MISMATCH", "La inscripción no pertenece al alumno o no es de la oferta del cargo");
        }
        if (!request.hasEnrollment() && ENROLLMENT_REASONS.contains(request.issuingReason())) {
            return PolicyVerdict.deny(ruleName(), "ENROLLMENT_REQUIRED", "Este cargo necesita la inscripción del alumno");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
