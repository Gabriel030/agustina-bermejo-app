package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.dto.BillingRunItemResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.service.BillingRunReader;
import com.red_escuela.backend.shared.UseCase;
import java.time.LocalDate;
import java.util.List;

@UseCase
public class BillingRunQueryService implements BillingRunQueryUseCase {

    private final BillingRunReader billingRunReader;

    public BillingRunQueryService(BillingRunReader billingRunReader) {
        this.billingRunReader = billingRunReader;
    }

    @Override
    public List<NightlyBillingRunResponse> listNightlyBillingRuns(LocalDate from, LocalDate to) {
        return billingRunReader.listNightlyBillingRuns(from, to);
    }

    @Override
    public NightlyBillingRunResponse findNightlyBillingRun(Long nightlyRunId) {
        return billingRunReader.findNightlyBillingRun(nightlyRunId);
    }

    @Override
    public List<BillingRunItemResponse> listBillingRunItems(Long nightlyRunId, BillingRunStep step) {
        return billingRunReader.listBillingRunItems(nightlyRunId, step);
    }
}
