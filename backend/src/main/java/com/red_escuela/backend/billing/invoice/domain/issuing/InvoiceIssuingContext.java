package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.ChargeKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class InvoiceIssuingContext {

    private final InvoiceIssuingRequest request;
    private final InvoiceIssuingFacts facts;
    private List<InvoiceDiscountCandidate> discountCandidates = List.of();
    private Map<Integer, InvoiceDiscountCandidate> winningDiscounts = Map.of();
    private Runnable discountReselection = () -> { };

    public InvoiceIssuingContext(InvoiceIssuingRequest request, InvoiceIssuingFacts facts) {
        this.request = Objects.requireNonNull(request, "request");
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    public InvoiceIssuingRequest request() {
        return request;
    }

    public InvoiceIssuingFacts facts() {
        return facts;
    }

    public ChargeKey chargeKey() {
        return request.chargeKey();
    }

    public boolean chargeKeyAlreadyUsed() {
        return facts.existingInvoiceId() != null;
    }

    public List<InvoiceDiscountCandidate> discountCandidates() {
        return discountCandidates;
    }

    public Map<Integer, InvoiceDiscountCandidate> winningDiscounts() {
        return winningDiscounts;
    }

    public void attachDiscountReselection(Runnable reselection) {
        this.discountReselection = Objects.requireNonNull(reselection, "reselection");
    }

    public void reselectDiscounts() {
        discountReselection.run();
    }

    public void recordDiscountSelection(List<InvoiceDiscountCandidate> candidates, Map<Integer, InvoiceDiscountCandidate> winnerByLine) {
        this.discountCandidates = List.copyOf(candidates);
        this.winningDiscounts = Collections.unmodifiableMap(new TreeMap<>(winnerByLine));
    }
}
