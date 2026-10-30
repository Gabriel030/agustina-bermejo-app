package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

public record MonthlyInvoiceSimulationResponse(Long runId, YearMonth period, LocalDate businessDate, LocalDate pricingDate, MonthlyInvoiceRunStatus status,
                                               MonthlyInvoiceRunTrigger trigger, LocalDateTime startedAt, LocalDateTime finishedAt,
                                               List<MonthlyInvoiceSimulationLineResponse> lines, List<MonthlyInvoiceSimulationAlertResponse> alerts) {

    public static MonthlyInvoiceSimulationResponse of(MonthlyInvoiceRun run, List<MonthlyInvoiceSimulationLineResponse> lines, List<MonthlyInvoiceSimulationAlertResponse> alerts) {
        return new MonthlyInvoiceSimulationResponse(run.getId(), YearMonth.from(run.getPeriod()), run.getBusinessDate(), run.getPricingDate(), run.getStatus(), run.getTrigger(),
                run.getStartedAt(), run.getFinishedAt(), lines, alerts);
    }
}
