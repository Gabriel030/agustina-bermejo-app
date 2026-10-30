package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetryWindow;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FailedMonthlyInvoiceRetrier {
    private static final Logger log = LoggerFactory.getLogger(FailedMonthlyInvoiceRetrier.class);
    static final int MAX_ITEMS_PER_TICK = 500;

    private final MonthlyInvoiceRunItemRepository itemRepository;
    private final FailedMonthlyInvoiceItemRetryProcessor retryProcessor;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public FailedMonthlyInvoiceRetrier(MonthlyInvoiceRunItemRepository itemRepository, FailedMonthlyInvoiceItemRetryProcessor retryProcessor, MeterRegistry meterRegistry,
                                       Clock clock) {
        this.itemRepository = itemRepository;
        this.retryProcessor = retryProcessor;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public int retryDueFailedItems() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!FailedInvoiceRetryWindow.isOpenAt(now.toLocalTime())) {
            return 0;
        }
        List<Long> dueItemIds = itemRepository.findDueFailedItemIds(now, PageRequest.of(0, MAX_ITEMS_PER_TICK));
        int retried = 0;
        for (Long itemId : dueItemIds) {
            retried += retryOneIsolatingFailure(itemId);
        }
        log.info("Reintentos de emisión mensual: candidatos={}, reintentados={}", dueItemIds.size(), retried);
        return retried;
    }

    private int retryOneIsolatingFailure(Long itemId) {
        try {
            return retryProcessor.retryItemIfDue(itemId).map(item -> {
                meterRegistry.counter("monthly_invoice_run_item_retries_total", "result", item.getResult().name()).increment();
                return 1;
            }).orElse(0);
        } catch (RuntimeException e) {
            log.error("Falló el reintento del ítem de la corrida mensual: ítem={}", itemId, e);
            return 0;
        }
    }
}
