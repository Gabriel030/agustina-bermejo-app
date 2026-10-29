package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.BillingRunAlertResponse;

import java.time.LocalDate;
import java.util.List;

public interface BillingRunAlertQueryUseCase {
    List<BillingRunAlertResponse> listBillingRunAlerts(LocalDate from, LocalDate to);
}
