package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.ChargeKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class InvoiceIssuingContext {

    private final InvoiceIssuingRequest request;
    private final InvoiceIssuingFacts facts;
    private List<InvoiceDiscountCandidate> discountCandidates = List.of();
    private InvoiceDiscountCandidate winningDiscount;

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

    public Optional<InvoiceDiscountCandidate> winningDiscount() {
        return Optional.ofNullable(winningDiscount);
    }

    public void recordDiscountSelection(List<InvoiceDiscountCandidate> candidates, Optional<InvoiceDiscountCandidate> winner) {
        this.discountCandidates = List.copyOf(candidates);
        this.winningDiscount = winner.orElse(null);
    }
}
