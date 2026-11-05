package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Service
public class MissedMonthlyInvoiceRunDetector {
    private static final Logger log = LoggerFactory.getLogger(MissedMonthlyInvoiceRunDetector.class);

    private final MonthlyInvoiceRunRepository runRepository;
    private final BillingRunAlertPublisher alertPublisher;

    public MissedMonthlyInvoiceRunDetector(MonthlyInvoiceRunRepository runRepository, BillingRunAlertPublisher alertPublisher) {
        this.runRepository = runRepository;
        this.alertPublisher = alertPublisher;
    }

    @Transactional
    public void alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(LocalDate businessDate) {
        LocalDate period = YearMonth.from(businessDate).atDay(1);
        Optional.of(period)
                .filter(firstDay -> !runRepository.existsByPeriodAndModeAndStatusNot(firstDay, MonthlyInvoiceRunMode.REAL, MonthlyInvoiceRunStatus.FAILED))
                .ifPresent(firstDay -> {
                    log.warn("La corrida mensual del periodo {} no corrio o fallo", YearMonth.from(firstDay));
                    alertPublisher.publishMonthlyRunMissedAlert(firstDay);
                });
    }
}
