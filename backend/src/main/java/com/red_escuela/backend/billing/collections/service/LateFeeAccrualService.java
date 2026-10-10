package com.red_escuela.backend.billing.collections.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class LateFeeAccrualService {
    private static final Logger log = LoggerFactory.getLogger(LateFeeAccrualService.class);

    private final LateFeeCandidateFinder candidateFinder;
    private final LateFeeIssuer issuer;

    public LateFeeAccrualService(LateFeeCandidateFinder candidateFinder, LateFeeIssuer issuer) {
        this.candidateFinder = candidateFinder;
        this.issuer = issuer;
    }

    public int reconcileLateFeesOfInvoice(Long originInvoiceId, LocalDate businessDate) {
        Set<Integer> attempted = new HashSet<>();
        int issued = 0;
        Optional<Integer> next = candidateFinder.findNextIssuableMonthOrdinal(originInvoiceId, businessDate, attempted);
        while (next.isPresent()) {
            attempted.add(next.get());
            if (issuer.issueLateFeeOfMonthInNewTransaction(originInvoiceId, next.get(), businessDate)) {
                issued++;
            }
            next = candidateFinder.findNextIssuableMonthOrdinal(originInvoiceId, businessDate, attempted);
        }
        if (issued > 0) {
            log.info("Recargos conciliados: origen={}, emitidos={}, fecha={}", originInvoiceId, issued, businessDate);
        }
        return issued;
    }
}
