package com.red_escuela.backend.billing.advanceenrollmentfee.usecase;

import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeAlerts;

public interface FindAdvanceEnrollmentFeeAlertsUseCase {
    AdvanceEnrollmentFeeAlerts find(Long templateId);
}
