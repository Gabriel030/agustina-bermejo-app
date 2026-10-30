package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetrySchedule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceItemOutcome;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemAttempt;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemAttemptRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

@Service
public class FailedMonthlyInvoiceItemRetryProcessor {
    private final MonthlyInvoiceRunItemRepository itemRepository;
    private final MonthlyInvoiceRunItemAttemptRepository attemptRepository;
    private final MonthlyInvoiceRunRepository runRepository;
    private final MonthlyInvoiceRunItemProcessor itemProcessor;
    private final FailedInvoiceRetryScheduleResolver scheduleResolver;
    private final BillingRunAlertPublisher alertPublisher;
    private final Clock clock;

    public FailedMonthlyInvoiceItemRetryProcessor(MonthlyInvoiceRunItemRepository itemRepository, MonthlyInvoiceRunItemAttemptRepository attemptRepository,
                                                  MonthlyInvoiceRunRepository runRepository, MonthlyInvoiceRunItemProcessor itemProcessor,
                                                  FailedInvoiceRetryScheduleResolver scheduleResolver, BillingRunAlertPublisher alertPublisher, Clock clock) {
        this.itemRepository = itemRepository;
        this.attemptRepository = attemptRepository;
        this.runRepository = runRepository;
        this.itemProcessor = itemProcessor;
        this.scheduleResolver = scheduleResolver;
        this.alertPublisher = alertPublisher;
        this.clock = clock;
    }

    @Transactional
    public Optional<MonthlyInvoiceRunItem> retryItemIfDue(Long itemId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<MonthlyInvoiceRunItem> locked = itemRepository.findByIdForUpdate(itemId).filter(item -> item.isDueForRetryAt(now));
        locked.ifPresent(item -> retryDueItem(item, now));
        return locked;
    }

    private void retryDueItem(MonthlyInvoiceRunItem item, LocalDateTime now) {
        MonthlyInvoiceRun run = runRepository.findById(item.getMonthlyInvoiceRunId()).orElseThrow();
        LocalDate pricingDate = run.getPeriod();
        MonthlyInvoiceItemOutcome outcome = itemProcessor.attemptEnrollmentMonthIssuance(item.getEnrollmentType(), item.getEnrollmentId(), YearMonth.from(pricingDate), pricingDate);
        FailedInvoiceRetrySchedule schedule = scheduleResolver.resolveScheduleEffectiveOn(now.toLocalDate());
        item.recordRetryOutcome(outcome, schedule, now);
        itemRepository.save(item);
        attemptRepository.save(MonthlyInvoiceRunItemAttempt.recordAttempt(item.getId(), item.getAttempts(), now, outcome));
        if (item.getResult() == MonthlyInvoiceRunItemResult.RETRIES_EXHAUSTED) {
            alertPublisher.publishItemRetriesExhaustedAlert(item);
        }
    }
}
