package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.dto.BillingRunItemResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingExecutionResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.usecase.BillingRunExecutionUseCase;
import com.red_escuela.backend.billing.run.usecase.BillingRunQueryUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
@RequestMapping("/billing-runs")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
public class BillingRunController {
    private final BillingRunQueryUseCase queryUseCase;
    private final BillingRunExecutionUseCase executionUseCase;

    public BillingRunController(BillingRunQueryUseCase queryUseCase, BillingRunExecutionUseCase executionUseCase) {
        this.queryUseCase = queryUseCase;
        this.executionUseCase = executionUseCase;
    }

    @GetMapping
    public List<NightlyBillingRunResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queryUseCase.listNightlyBillingRuns(from, to);
    }

    @PostMapping("/executions")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<NightlyBillingExecutionResponse> executeTodaysNightlyRun() {
        NightlyBillingExecutionResponse execution = executionUseCase.executeTodaysNightlyBillingRunUnlessItExists();
        return ResponseEntity.status(execution.started() ? HttpStatus.CREATED : HttpStatus.OK).body(execution);
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
