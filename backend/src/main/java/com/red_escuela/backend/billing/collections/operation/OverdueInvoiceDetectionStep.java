package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.OverdueInvoiceTransitionService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OverdueInvoiceDetectionStep {
    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceDetectionStep.class);
    private static final int PAGE_SIZE = 200;

    private final InvoiceRepository invoiceRepository;
    private final OverdueInvoiceTransitionService transitionService;

    public OverdueInvoiceDetectionStep(InvoiceRepository invoiceRepository, OverdueInvoiceTransitionService transitionService) {
        this.invoiceRepository = invoiceRepository;
        this.transitionService = transitionService;
    }

    public OverdueDetectionResult detectOverdueInvoices(LocalDate today) {
        int candidates = 0;
        int marked = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        Long lastId = 0L;
        while (true) {
            List<Invoice> batch = invoiceRepository.findByPaymentStatusAndDueDateBeforeAndIdGreaterThanOrderByIdAsc(
                    InvoicePaymentStatus.PENDING, today, lastId, PageRequest.of(0, PAGE_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            for (Invoice invoice : batch) {
                try {
                    if (transitionService.markAsOverdueWhenPastDueDate(invoice.getId(), today)) {
                        marked++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Falló la detección de vencida para la factura id={}", invoice.getId(), e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("invoiceId", invoice.getId());
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
        return new OverdueDetectionResult(candidates, marked, failed, errors);
    }
}
