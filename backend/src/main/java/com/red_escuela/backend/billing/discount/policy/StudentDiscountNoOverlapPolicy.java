package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"STUDENT_DISCOUNT_OVERLAP"}, decisions = {"D-401"})
public final class StudentDiscountNoOverlapPolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "StudentDiscountNoOverlapPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        StudentDiscountGrantCheck.OverlappingDiscount overlap = check.overlappingDiscount();
        return overlap == null
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "STUDENT_DISCOUNT_OVERLAP",
                        "La inscripción ya tiene un descuento vigente del mismo tipo (n.º " + overlap.id() + ") de " + overlap.from() + " a " + overlap.to()
                                + " que se superpone con el rango pedido. Revocá el existente antes de otorgar otro.");
    }
}
