package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.LateFeeCutoff;
import com.red_escuela.backend.billing.collections.service.LateFeeAccrualService;
import com.red_escuela.backend.billing.collections.service.LatePaymentTimeline;
import com.red_escuela.backend.billing.invoice.service.PendingInvoiceQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LateFeeAccrualStep {
    private static final Logger log = LoggerFactory.getLogger(LateFeeAccrualStep.class);
    private static final int PAGE_SIZE = 200;
    private static final int SETTLEMENT_RECOVERY_WINDOW_DAYS = 35;

    private final PendingInvoiceQueryService overdueInvoiceQueryService;
    private final LateFeeAccrualService accrualService;

    public LateFeeAccrualStep(PendingInvoiceQueryService overdueInvoiceQueryService, LateFeeAccrualService accrualService) {
        this.overdueInvoiceQueryService = overdueInvoiceQueryService;
        this.accrualService = accrualService;
    }

    public LateFeeAccrualResult accrueLateFees(LocalDate today) {
        return accrueLateFees(today, HeldSubjectFinder.NONE);
    }

    public LateFeeAccrualResult accrueLateFees(LocalDate today, HeldSubjectFinder heldInvoices) {
        int candidates = 0;
        int issued = 0;
        int failed = 0;
        int recovered = 0;
        int settlementCandidates = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        LatePaymentTimeline timeline = accrualService.timelineOf(today);
        LocalDate cutoffDay = LateFeeCutoff.closingOfMonthBefore(today).lastDay();
        Long lastId = 0L;
        while (true) {
            List<Long> batch = overdueInvoiceQueryService.findOverdueLateFeeOriginIdsNotCoveredUpToAfter(lastId, cutoffDay, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            Set<Long> held = heldInvoices.findHeldAmong(batch);
            for (Long originId : batch) {
                if (held.contains(originId)) {
                    errors.add(heldInvoiceError(originId));
                    continue;
                }
                try {
                    issued += accrualService.reconcileLateFeesOfInvoice(originId, today, timeline);
                } catch (Exception e) {
                    failed++;
                    log.error("Falló la conciliación de recargos para la factura id={}", originId, e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("invoiceId", originId);
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1);
        }
        lastId = 0L;
        LocalDate since = today.minusDays(SETTLEMENT_RECOVERY_WINDOW_DAYS);
        while (true) {
            List<Long> batch = accrualService.findRecentlySettledOriginIdsAfter(lastId, since, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            settlementCandidates += batch.size();
            Set<Long> held = heldInvoices.findHeldAmong(batch);
            for (Long originId : batch) {
                if (held.contains(originId)) {
                    errors.add(heldInvoiceError(originId));
                    continue;
                }
                try {
                    recovered += accrualService.reconcileLateFeesOfSettledInvoice(originId, today, timeline);
                } catch (Exception e) {
                    failed++;
                    log.error("Falló la recuperación del recargo final de la factura id={}", originId, e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("invoiceId", originId);
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1);
        }
        return new LateFeeAccrualResult(candidates, issued + recovered, failed, errors, recovered, settlementCandidates);
    }

    private static Map<String, Object> heldInvoiceError(Long invoiceId) {
        Map<String, Object> heldInvoice = new LinkedHashMap<>();
        heldInvoice.put("invoiceId", invoiceId);
        heldInvoice.put("decisionCode", HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE);
        heldInvoice.put("message", HeldSubjectFinder.INCONSISTENT_ACCOUNT_MESSAGE);
        return heldInvoice;
    }
}
