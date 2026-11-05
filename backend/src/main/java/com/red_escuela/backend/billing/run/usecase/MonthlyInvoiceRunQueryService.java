package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.dto.RunPage;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunReader;
import com.red_escuela.backend.shared.UseCase;
import java.time.YearMonth;

@UseCase
public class MonthlyInvoiceRunQueryService implements MonthlyInvoiceRunQueryUseCase {

    public static final int DEFAULT_PAGE_SIZE = MonthlyInvoiceRunReader.DEFAULT_PAGE_SIZE;
    public static final int MAX_PAGE_SIZE = MonthlyInvoiceRunReader.MAX_PAGE_SIZE;

    private final MonthlyInvoiceRunReader monthlyInvoiceRunReader;

    public MonthlyInvoiceRunQueryService(MonthlyInvoiceRunReader monthlyInvoiceRunReader) {
        this.monthlyInvoiceRunReader = monthlyInvoiceRunReader;
    }

    @Override
    public RunPage<MonthlyInvoiceRunResponse> listMonthlyInvoiceRuns(YearMonth period, Integer page, Integer size) {
        return monthlyInvoiceRunReader.listMonthlyInvoiceRuns(period, page, size);
    }

    @Override
    public MonthlyInvoiceRunResponse findMonthlyInvoiceRun(Long monthlyRunId) {
        return monthlyInvoiceRunReader.findMonthlyInvoiceRun(monthlyRunId);
    }

    @Override
    public RunPage<MonthlyInvoiceRunItemResponse> listMonthlyInvoiceRunItems(Long monthlyRunId, MonthlyInvoiceRunItemResult result, Integer page, Integer size) {
        return monthlyInvoiceRunReader.listMonthlyInvoiceRunItems(monthlyRunId, result, page, size);
    }
}
