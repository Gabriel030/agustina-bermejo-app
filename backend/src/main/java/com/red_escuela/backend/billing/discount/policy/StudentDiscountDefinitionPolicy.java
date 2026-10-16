package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVALID_STUDENT_DISCOUNT", "DISCOUNT_DEFINITION_CLOSED"}, decisions = {"D-217", "D-384"})
public final class StudentDiscountDefinitionPolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "StudentDiscountDefinitionPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        return switch (check.definitionFault()) {
            case NONE -> PolicyVerdict.allow(ruleName());
            case MISSING -> PolicyVerdict.deny(ruleName(), "INVALID_STUDENT_DISCOUNT", "No hay una definición de beca vigente.");
            case NOT_A_SCHOLARSHIP -> PolicyVerdict.deny(ruleName(), "INVALID_STUDENT_DISCOUNT", "La promoción indicada no es una beca.");
            case UNEXPECTED_FOR_INDIVIDUAL -> PolicyVerdict.deny(ruleName(), "INVALID_STUDENT_DISCOUNT", "El descuento individual no usa una promoción del catálogo.");
            case CLOSED -> PolicyVerdict.deny(ruleName(), "DISCOUNT_DEFINITION_CLOSED", "La promoción está cerrada.");
        };
    }
}
