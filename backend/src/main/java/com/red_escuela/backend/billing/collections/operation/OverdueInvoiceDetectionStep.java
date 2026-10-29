package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.OverdueInvoiceTransitionService;
import com.red_escuela.backend.billing.collections.service.OverdueTransitionOutcome;
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
public class OverdueInvoiceDetectionStep {
    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceDetectionStep.class);
    private static final int PAGE_SIZE = 200;
    public static final String SKIPPED_WITHOUT_LATE_PAYMENT_TERMS_CODE = "NO_LATE_PAYMENT_TERMS";

    private final PendingInvoiceQueryService pendingInvoiceQueryService;
    private final OverdueInvoiceTransitionService transitionService;

    public OverdueInvoiceDetectionStep(PendingInvoiceQueryService pendingInvoiceQueryService, OverdueInvoiceTransitionService transitionService) {
        this.pendingInvoiceQueryService = pendingInvoiceQueryService;
        this.transitionService = transitionService;
    }

    public OverdueDetectionResult detectOverdueInvoices(LocalDate today) {
        return detectOverdueInvoices(today, HeldSubjectFinder.NONE);
    }

    public OverdueDetectionResult detectOverdueInvoices(LocalDate today, HeldSubjectFinder heldInvoices) {
        int candidates = 0;
        int marked = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        Long lastId = 0L;
        while (true) {
            List<Long> batch = pendingInvoiceQueryService.findPendingInvoiceIdsDueBefore(today, lastId, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            Set<Long> held = heldInvoices.findHeldAmong(batch);
            for (Long invoiceId : batch) {
                if (held.contains(invoiceId)) {
                    skipped++;
                    Map<String, Object> heldInvoice = new LinkedHashMap<>();
                    heldInvoice.put("invoiceId", invoiceId);
                    heldInvoice.put("decisionCode", HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE);
                    heldInvoice.put("message", HeldSubjectFinder.INCONSISTENT_ACCOUNT_MESSAGE);
                    errors.add(heldInvoice);
                    continue;
                }
                try {
                    OverdueTransitionOutcome outcome = transitionService.markAsOverdueWhenPastDueDate(invoiceId, today);
                    if (outcome == OverdueTransitionOutcome.MARKED_AS_OVERDUE) {
                        marked++;
                    } else if (outcome == OverdueTransitionOutcome.SKIPPED_WITHOUT_LATE_PAYMENT_TERMS) {
                        skipped++;
                        Map<String, Object> skippedInvoice = new LinkedHashMap<>();
                        skippedInvoice.put("invoiceId", invoiceId);
                        skippedInvoice.put("decisionCode", SKIPPED_WITHOUT_LATE_PAYMENT_TERMS_CODE);
                        skippedInvoice.put("message", "Sin versión de mora aplicable: no se evaluó su vencimiento");
                        errors.add(skippedInvoice);
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Falló la detección de vencida para la factura id={}", invoiceId, e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("invoiceId", invoiceId);
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1);
        }
        return new OverdueDetectionResult(candidates, marked, skipped, failed, errors);
    }
}
