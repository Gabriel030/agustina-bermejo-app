package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record MonthlyInvoiceRunResponse(Long id, LocalDate period, MonthlyInvoiceRunMode mode, MonthlyInvoiceIssuanceMode issuanceMode, LocalDate businessDate,
                                        LocalDate pricingDate, MonthlyInvoiceRunStatus status, MonthlyInvoiceRunTrigger trigger, String failureCode, LocalDateTime startedAt,
                                        LocalDateTime finishedAt, MonthlyInvoiceRunItemCountersResponse counters) {

    public static MonthlyInvoiceRunResponse of(MonthlyInvoiceRun run, Map<MonthlyInvoiceRunItemResult, Long> countByResult) {
        return new MonthlyInvoiceRunResponse(run.getId(), run.getPeriod(), run.getMode(), run.getIssuanceMode(), run.getBusinessDate(), run.getPricingDate(), run.getStatus(),
                run.getTrigger(), run.getFailureCode(), run.getStartedAt(), run.getFinishedAt(), MonthlyInvoiceRunItemCountersResponse.of(countByResult));
    }
}
