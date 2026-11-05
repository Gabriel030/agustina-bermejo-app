package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

final class InvoiceDiscountStrategiesByOrigin {

    private final Map<DiscountCandidateOrigin, InvoiceDiscountCandidateStrategy> strategiesByOrigin = new EnumMap<>(DiscountCandidateOrigin.class);

    InvoiceDiscountStrategiesByOrigin(List<InvoiceDiscountCandidateStrategy> strategies) {
        strategies.forEach(strategy -> {
            if (strategiesByOrigin.put(strategy.origin(), strategy) != null) {
                throw new IllegalStateException("More than one discount strategy declares the origin " + strategy.origin());
            }
        });
    }

    Stream<InvoiceDiscountCandidateStrategy> all() {
        return strategiesByOrigin.values().stream();
    }

    Map<Integer, InvoiceDiscountCandidate> registerWinnersBeforeInvoiceIsAssembled(Map<Integer, InvoiceDiscountCandidate> winnerByLine, InvoiceIssuingContext context) {
        Map<Integer, InvoiceDiscountCandidate> registered = new TreeMap<>();
        winnerByLine.forEach((lineNumber, winner) -> registered.put(lineNumber, strategiesByOrigin.get(winner.origin()).registerWinnerBeforeInvoiceIsAssembled(winner, context)));
        return registered;
    }

    void markWinnersAsApplied(Collection<InvoiceDiscountCandidate> winners) {
        winners.forEach(winner -> strategiesByOrigin.get(winner.origin()).markDiscountAsApplied(winner));
    }
}
