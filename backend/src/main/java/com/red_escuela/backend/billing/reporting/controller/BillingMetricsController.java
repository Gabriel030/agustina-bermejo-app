package com.red_escuela.backend.billing.reporting.controller;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.reporting.dto.MonthlyMetricResponse;
import com.red_escuela.backend.billing.reporting.exception.UnknownMetricPanelException;
import com.red_escuela.backend.billing.reporting.usecase.GetMonthlyBillingMetricsCommand;
import com.red_escuela.backend.billing.reporting.usecase.GetMonthlyBillingMetricsUseCase;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/billing/metrics")
@PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
public class BillingMetricsController {

    private final GetMonthlyBillingMetricsUseCase getMonthlyBillingMetrics;

    public BillingMetricsController(GetMonthlyBillingMetricsUseCase getMonthlyBillingMetrics) {
        this.getMonthlyBillingMetrics = getMonthlyBillingMetrics;
    }

    @GetMapping("/{panel}")
    public MonthlyMetricResponse getPanel(@PathVariable String panel,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to,
                                          @RequestParam(required = false) Currency currency,
                                          @RequestParam(required = false) Long programId,
                                          @RequestParam(required = false) Long courseId,
                                          @RequestParam(required = false) InvoiceType invoiceType) {
        return MonthlyMetricResponse.from(getMonthlyBillingMetrics.getMonthlyBillingMetrics(
                new GetMonthlyBillingMetricsCommand(panel, from, to, currency, programId, courseId, invoiceType)));
    }

    @ExceptionHandler(UnknownMetricPanelException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorCodeResponse handleUnknownPanel(UnknownMetricPanelException exception) {
        return ErrorCodeResponse.of(exception);
    }
}
