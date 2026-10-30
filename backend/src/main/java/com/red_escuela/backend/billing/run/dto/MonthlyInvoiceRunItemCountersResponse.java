package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;

import java.util.Map;

public record MonthlyInvoiceRunItemCountersResponse(long issued, long notApplicable, long alreadyIssued, long failed, long retriesExhausted) {

    public static MonthlyInvoiceRunItemCountersResponse of(Map<MonthlyInvoiceRunItemResult, Long> countByResult) {
        return new MonthlyInvoiceRunItemCountersResponse(
                countByResult.getOrDefault(MonthlyInvoiceRunItemResult.ISSUED, 0L),
                countByResult.getOrDefault(MonthlyInvoiceRunItemResult.NOT_APPLICABLE, 0L),
                countByResult.getOrDefault(MonthlyInvoiceRunItemResult.ALREADY_ISSUED, 0L),
                countByResult.getOrDefault(MonthlyInvoiceRunItemResult.FAILED, 0L),
                countByResult.getOrDefault(MonthlyInvoiceRunItemResult.RETRIES_EXHAUSTED, 0L));
    }
}
