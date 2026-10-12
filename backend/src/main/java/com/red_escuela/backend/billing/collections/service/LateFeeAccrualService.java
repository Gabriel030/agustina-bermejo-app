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
    private final LatePaymentVersionResolver versionResolver;
    private final LateFeeOverchargeRecorder overchargeRecorder;

    public LateFeeAccrualService(LateFeeCandidateFinder candidateFinder, LateFeeIssuer issuer, LatePaymentVersionResolver versionResolver,
                                 LateFeeOverchargeRecorder overchargeRecorder) {
        this.candidateFinder = candidateFinder;
        this.issuer = issuer;
        this.versionResolver = versionResolver;
        this.overchargeRecorder = overchargeRecorder;
    }

    public int reconcileLateFeesOfSettledInvoice(Long originInvoiceId, LocalDate businessDate) {
        int issued = reconcileLateFeesOfInvoice(originInvoiceId, businessDate);
        overchargeRecorder.recordDaysChargedAfterThePaymentOfInvoice(originInvoiceId);
        return issued;
    }

    public int reconcileLateFeesOfInvoice(Long originInvoiceId, LocalDate businessDate) {
        return reconcileLateFeesOfInvoice(originInvoiceId, businessDate, timelineOf(businessDate));
    }

    public LatePaymentTimeline timelineOf(LocalDate businessDate) {
        return versionResolver.timelineUpTo(businessDate);
    }

    public int reconcileLateFeesOfInvoice(Long originInvoiceId, LocalDate businessDate, LatePaymentTimeline timeline) {
        Set<LocalDate> attempted = new HashSet<>();
        int issued = 0;
        Optional<LocalDate> next = candidateFinder.findNextIssuablePeriodStart(originInvoiceId, businessDate, attempted, timeline);
        while (next.isPresent()) {
            attempted.add(next.get());
            if (issuer.issueLateFeeOfPeriodInNewTransaction(originInvoiceId, next.get(), businessDate, timeline)) {
                issued++;
            }
            next = candidateFinder.findNextIssuablePeriodStart(originInvoiceId, businessDate, attempted, timeline);
        }
        if (issued > 0) {
            log.info("Recargos conciliados: origen={}, emitidos={}, fecha={}", originInvoiceId, issued, businessDate);
        }
        return issued;
    }
}
