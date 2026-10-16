package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class InvoiceDiscountCalculator {

    private final PolicySet<DiscountContest> discountSelectionPolicies;

    public InvoiceDiscountCalculator(PolicySet<DiscountContest> discountSelectionPolicies) {
        this.discountSelectionPolicies = discountSelectionPolicies;
    }

    public Map<Integer, InvoiceDiscountCandidate> selectBestDiscountPerInvoiceLine(InvoiceType invoiceType, List<InvoiceDiscountCandidate> candidates) {
        Map<Integer, InvoiceDiscountCandidate> winnerByLine = new TreeMap<>();
        candidates.stream()
                .filter(candidate -> discountSelectionPolicies
                        .evaluateRulesUntilFirstViolation(new DiscountContest(invoiceType, candidate, rivalsOf(candidate, candidates)))
                        .allowed())
                .forEach(candidate -> winnerByLine.putIfAbsent(candidate.lineNumber(), candidate));
        return Collections.unmodifiableMap(winnerByLine);
    }

    private List<InvoiceDiscountCandidate> rivalsOf(InvoiceDiscountCandidate candidate, List<InvoiceDiscountCandidate> candidates) {
        return candidates.stream().filter(other -> other != candidate).toList();
    }
}
