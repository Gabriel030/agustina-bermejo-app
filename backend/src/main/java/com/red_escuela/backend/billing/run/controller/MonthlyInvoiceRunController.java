package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.DraftInvoiceGroupResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.dto.RunPage;
import com.red_escuela.backend.billing.run.usecase.ManualMonthlyInvoiceItemRetryUseCase;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceRunDraftQueryUseCase;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceRunQueryUseCase;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/monthly-invoice-runs")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
public class MonthlyInvoiceRunController {
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final MonthlyInvoiceRunQueryUseCase queryUseCase;
    private final ManualMonthlyInvoiceItemRetryUseCase manualRetryUseCase;
    private final MonthlyInvoiceRunDraftQueryUseCase draftQueryUseCase;

    public MonthlyInvoiceRunController(MonthlyInvoiceRunQueryUseCase queryUseCase, ManualMonthlyInvoiceItemRetryUseCase manualRetryUseCase,
                                      MonthlyInvoiceRunDraftQueryUseCase draftQueryUseCase) {
        this.queryUseCase = queryUseCase;
        this.manualRetryUseCase = manualRetryUseCase;
        this.draftQueryUseCase = draftQueryUseCase;
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "Corridas mensuales, la más nueva primero; el total sin paginar viaja en el header X-Total-Count",
            headers = @Header(name = TOTAL_COUNT_HEADER, description = "Cantidad total de corridas que cumplen el filtro, sin paginar", schema = @Schema(type = "integer", format = "int64")))
    public ResponseEntity<List<MonthlyInvoiceRunResponse>> list(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
                                                                @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        RunPage<MonthlyInvoiceRunResponse> runs = queryUseCase.listMonthlyInvoiceRuns(period, page, size);
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, Long.toString(runs.totalElements())).body(runs.items());
    }

    @GetMapping("/{monthlyRunId}")
    public MonthlyInvoiceRunResponse find(@PathVariable Long monthlyRunId) {
        return queryUseCase.findMonthlyInvoiceRun(monthlyRunId);
    }

    @GetMapping("/{monthlyRunId}/items")
    @ApiResponse(responseCode = "200", description = "Ítems de la corrida por id ascendente; el total sin paginar viaja en el header X-Total-Count",
            headers = @Header(name = TOTAL_COUNT_HEADER, description = "Cantidad total de ítems que cumplen el filtro, sin paginar", schema = @Schema(type = "integer", format = "int64")))
    public ResponseEntity<List<MonthlyInvoiceRunItemResponse>> listItems(@PathVariable Long monthlyRunId, @RequestParam(required = false) MonthlyInvoiceRunItemResult result,
                                                                         @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        RunPage<MonthlyInvoiceRunItemResponse> items = queryUseCase.listMonthlyInvoiceRunItems(monthlyRunId, result, page, size);
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, Long.toString(items.totalElements())).body(items.items());
    }

    @GetMapping("/{monthlyRunId}/draft-invoices")
    public List<DraftInvoiceGroupResponse> listDraftInvoiceGroups(@PathVariable Long monthlyRunId) {
        return draftQueryUseCase.listDraftInvoiceGroups(monthlyRunId);
    }

    @PostMapping("/{monthlyRunId}/items/{itemId}/retries")
    public MonthlyInvoiceRunItemResponse retryItem(@PathVariable Long monthlyRunId, @PathVariable Long itemId) {
        return manualRetryUseCase.retryMonthlyInvoiceRunItemManually(monthlyRunId, itemId);
    }
}
