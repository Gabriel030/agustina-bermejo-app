package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.math.BigDecimal;
import java.util.Optional;

@PolicyRule(violationCodes = {"INVALID_DISCOUNT_DEFINITION"}, decisions = {"D-075", "D-129", "D-171", "D-240"})
public final class DiscountDefinitionCoherencePolicy implements Policy<DiscountDefinitionCheck> {
    private static final String CODE = "INVALID_DISCOUNT_DEFINITION";
    private static final int MAX_NAME_LENGTH = 200;
    private static final BigDecimal MAX_CAP_AMOUNT = new BigDecimal("9999999999.99");

    @Override
    public String ruleName() {
        return "DiscountDefinitionCoherencePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountDefinitionCheck check) {
        return findProblem(check.draft())
                .map(problem -> PolicyVerdict.deny(ruleName(), CODE, problem))
                .orElseGet(() -> PolicyVerdict.allow(ruleName()));
    }

    private Optional<String> findProblem(DiscountDefinitionDraft draft) {
        if (draft.name() == null || draft.name().isBlank() || draft.name().strip().length() > MAX_NAME_LENGTH) {
            return Optional.of("El nombre es obligatorio y admite hasta 200 caracteres.");
        }
        if (draft.discountType() == null || draft.applicableInvoiceType() == null) {
            return Optional.of("El tipo de promoción y el tipo de factura son obligatorios.");
        }
        if (draft.applicableInvoiceType() == InvoiceType.LATE_FEE) {
            return Optional.of("Ninguna promoción aplica a un recargo.");
        }
        if (draft.discountType() == DiscountDefinitionType.ANNUAL_UPFRONT && draft.applicableInvoiceType() != InvoiceType.ANNUAL_UPFRONT) {
            return Optional.of("La promoción de pago anual solo aplica a la factura de pago anual.");
        }
        Optional<String> calculationProblem = findCalculationProblem(draft);
        if (calculationProblem.isPresent()) {
            return calculationProblem;
        }
        if (draft.capPerInvoice() != null && (draft.capPerInvoice().isNegative() || draft.capPerInvoice().isZero())) {
            return Optional.of("El tope por factura debe ser mayor a cero.");
        }
        if (draft.capPerInvoice() != null && draft.capPerInvoice().amount().compareTo(MAX_CAP_AMOUNT) > 0) {
            return Optional.of("El tope por factura admite hasta 9.999.999.999,99.");
        }
        if (draft.priority() < 1) {
            return Optional.of("La prioridad empieza en 1.");
        }
        if (draft.quota() != null && draft.quota() < 1) {
            return Optional.of("El cupo es de al menos una persona.");
        }
        if (draft.installmentsTotal() != null && draft.installmentsTotal() < 1) {
            return Optional.of("La cantidad de cuotas es de al menos 1.");
        }
        return Optional.empty();
    }

    private Optional<String> findCalculationProblem(DiscountDefinitionDraft draft) {
        MonetaryAdjustment calculation = draft.calculation();
        if (calculation == null) {
            return draft.discountType() == DiscountDefinitionType.SCHOLARSHIP ? Optional.empty() : Optional.of("El cálculo es obligatorio.");
        }
        boolean zero = switch (calculation) {
            case MonetaryAdjustment.Proportional proportional -> proportional.rate().fraction().signum() == 0;
            case MonetaryAdjustment.Nominal nominal -> nominal.amount().isZero();
        };
        return zero ? Optional.of("El porcentaje o el monto debe ser mayor a cero.") : Optional.empty();
    }
}
