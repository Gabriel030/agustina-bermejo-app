package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.dto.BillingRunItemResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.usecase.BillingRunQueryUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/billing-runs")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
public class BillingRunController {
    private final BillingRunQueryUseCase queryUseCase;

    public BillingRunController(BillingRunQueryUseCase queryUseCase) {
        this.queryUseCase = queryUseCase;
    }

    @GetMapping
    public List<NightlyBillingRunResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queryUseCase.listNightlyBillingRuns(from, to);
    }

    @GetMapping("/{nightlyRunId}")
    public NightlyBillingRunResponse find(@PathVariable Long nightlyRunId) {
        return queryUseCase.findNightlyBillingRun(nightlyRunId);
    }

    @GetMapping("/{nightlyRunId}/steps/{step}/items")
    public List<BillingRunItemResponse> listItems(@PathVariable Long nightlyRunId, @PathVariable BillingRunStep step) {
        return queryUseCase.listBillingRunItems(nightlyRunId, step);
    }
}
