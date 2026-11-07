package com.red_escuela.backend.billing.advanceenrollmentfee.usecase;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeAlertsReader;
import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeAlerts;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class FindAdvanceEnrollmentFeeAlertsService implements FindAdvanceEnrollmentFeeAlertsUseCase {
    private final AdvanceEnrollmentFeeAlertsReader alertsReader;

    public FindAdvanceEnrollmentFeeAlertsService(AdvanceEnrollmentFeeAlertsReader alertsReader) {
        this.alertsReader = alertsReader;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public AdvanceEnrollmentFeeAlerts find(Long templateId) {
        return alertsReader.alertsOf(templateId);
    }
}
