package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.BillingRunAlertResponse;
import com.red_escuela.backend.billing.run.service.BillingRunAlertReader;
import com.red_escuela.backend.shared.UseCase;
import java.time.LocalDate;
import java.util.List;

@UseCase
public class BillingRunAlertQueryService implements BillingRunAlertQueryUseCase {

    private final BillingRunAlertReader billingRunAlertReader;

    public BillingRunAlertQueryService(BillingRunAlertReader billingRunAlertReader) {
        this.billingRunAlertReader = billingRunAlertReader;
    }

    @Override
    public List<BillingRunAlertResponse> listBillingRunAlerts(LocalDate from, LocalDate to) {
        return billingRunAlertReader.listBillingRunAlerts(from, to);
    }
}
