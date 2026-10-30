package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceRunQueryUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/monthly-invoice-runs")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
public class MonthlyInvoiceRunController {
    private final MonthlyInvoiceRunQueryUseCase queryUseCase;

    public MonthlyInvoiceRunController(MonthlyInvoiceRunQueryUseCase queryUseCase) {
        this.queryUseCase = queryUseCase;
    }

    @GetMapping("/{monthlyRunId}/items")
    public List<MonthlyInvoiceRunItemResponse> listItems(@PathVariable Long monthlyRunId, @RequestParam(required = false) MonthlyInvoiceRunItemResult result) {
        return queryUseCase.listMonthlyInvoiceRunItems(monthlyRunId, result);
    }
}
