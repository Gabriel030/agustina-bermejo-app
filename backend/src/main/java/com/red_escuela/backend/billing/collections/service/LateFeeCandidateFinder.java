package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
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
import java.util.List;
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
    public Optional<LocalDate> findNextIssuablePeriodStart(Long originInvoiceId, LocalDate businessDate, Set<LocalDate> excludedPeriodStarts) {
        Invoice origin = invoiceAccess.findInvoice(originInvoiceId).orElseThrow(() -> new InvoiceNotFoundException(originInvoiceId));
        List<LateFeeAccrualPeriod> covered = chargeRepository.findCoveredPeriodsByOriginInvoiceId(originInvoiceId);
        return contextLoader.loadMissingPeriodContexts(origin, covered, businessDate).stream()
                .filter(context -> !excludedPeriodStarts.contains(context.period().period().firstDay()))
                .filter(context -> !context.lateFeeAmount().value().isZero())
                .filter(context -> {
                    boolean allowed = issuingPolicies.evaluateRulesUntilFirstViolation(context).allowed();
                    if (!allowed) {
                        log.debug("Recargo no corresponde: origen={}, periodo={}", originInvoiceId, context.period().period());
                    }
                    return allowed;
                })
                .map(context -> context.period().period().firstDay())
                .findFirst();
    }
}
