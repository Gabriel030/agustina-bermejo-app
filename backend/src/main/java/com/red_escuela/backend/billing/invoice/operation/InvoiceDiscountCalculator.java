package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class InvoiceDiscountCalculator {

    private final PolicySet<DiscountContest> discountSelectionPolicies;

    public InvoiceDiscountCalculator(PolicySet<DiscountContest> discountSelectionPolicies) {
        this.discountSelectionPolicies = discountSelectionPolicies;
    }

    public Optional<InvoiceDiscountCandidate> selectBestDiscountForInvoice(InvoiceType invoiceType, List<InvoiceDiscountCandidate> candidates) {
        List<InvoiceDiscountCandidate> sameInvoiceType = candidates.stream()
                .filter(candidate -> candidate.applicableInvoiceType() == invoiceType)
                .toList();
        return candidates.stream()
                .filter(candidate -> discountSelectionPolicies
                        .evaluateRulesUntilFirstViolation(new DiscountContest(invoiceType, candidate, rivalsOf(candidate, sameInvoiceType)))
                        .allowed())
                .findFirst();
    }

    private List<InvoiceDiscountCandidate> rivalsOf(InvoiceDiscountCandidate candidate, List<InvoiceDiscountCandidate> sameInvoiceType) {
        return sameInvoiceType.stream().filter(other -> other != candidate).toList();
    }
}
