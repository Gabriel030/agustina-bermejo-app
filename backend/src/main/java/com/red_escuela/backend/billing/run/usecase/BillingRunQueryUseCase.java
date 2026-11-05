package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.dto.BillingRunItemResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;

import java.time.LocalDate;
import java.util.List;

public interface BillingRunQueryUseCase {
    List<NightlyBillingRunResponse> listNightlyBillingRuns(LocalDate from, LocalDate to);

    NightlyBillingRunResponse findNightlyBillingRun(Long nightlyRunId);

    List<BillingRunItemResponse> listBillingRunItems(Long nightlyRunId, BillingRunStep step);
}
