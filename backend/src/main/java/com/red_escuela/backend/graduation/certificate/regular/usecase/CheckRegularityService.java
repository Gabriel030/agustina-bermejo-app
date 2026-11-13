package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityEvaluator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CheckRegularityService implements CheckRegularityUseCase {

    private final RegularityEvaluator regularityEvaluator;
    private final BillingModeGate billingModeGate;

    public CheckRegularityService(RegularityEvaluator regularityEvaluator, BillingModeGate billingModeGate) {
        this.regularityEvaluator = regularityEvaluator;
        this.billingModeGate = billingModeGate;
    }

    @Override
    public RegularityResponse check(RegularityKind kind, Long enrollmentId) {
        RegularityVerdict verdict = regularityEvaluator.evaluate(kind, enrollmentId);
        return new RegularityResponse(verdict.kind(), enrollmentId, verdict.institutionallyRegular(), verdict.reasons(),
                verdict.attendanceControlled(), billingModeGate.currentMode().name());
    }
}
