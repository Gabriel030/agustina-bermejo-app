package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.policy.LateFeeIssuingContext;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@Component
public class LateFeeCandidateFinder {
    private static final Logger log = LoggerFactory.getLogger(LateFeeCandidateFinder.class);

    private final InvoiceCreditAccessService invoiceAccess;
    private final LateFeeChargeRepository chargeRepository;
    private final LateFeeContextLoader contextLoader;
    private final PolicySet<LateFeeIssuingContext> issuingPolicies;

    public LateFeeCandidateFinder(InvoiceCreditAccessService invoiceAccess, LateFeeChargeRepository chargeRepository, LateFeeContextLoader contextLoader,
                                  PolicySet<LateFeeIssuingContext> debitNoteIssuingPolicySet) {
        this.invoiceAccess = invoiceAccess;
        this.chargeRepository = chargeRepository;
        this.contextLoader = contextLoader;
        this.issuingPolicies = debitNoteIssuingPolicySet;
    }

    @Transactional(readOnly = true)
    public Optional<Integer> findNextIssuableMonthOrdinal(Long originInvoiceId, LocalDate businessDate, Set<Integer> excludedOrdinals) {
        Invoice origin = invoiceAccess.findInvoice(originInvoiceId).orElseThrow(() -> new InvoiceNotFoundException(originInvoiceId));
        Set<Integer> charged = chargeRepository.findMonthOrdinalsByOriginInvoiceId(originInvoiceId);
        return contextLoader.loadMissingMonthContexts(origin, charged, businessDate).stream()
                .filter(context -> !excludedOrdinals.contains(context.month().ordinal()))
                .filter(context -> !context.lateFeeAmount().value().isZero())
                .filter(context -> {
                    boolean allowed = issuingPolicies.evaluateRulesUntilFirstViolation(context).allowed();
                    if (!allowed) {
                        log.debug("Recargo no corresponde: origen={}, mes={}", originInvoiceId, context.month().ordinal());
                    }
                    return allowed;
                })
                .map(context -> context.month().ordinal())
                .findFirst();
    }
}
