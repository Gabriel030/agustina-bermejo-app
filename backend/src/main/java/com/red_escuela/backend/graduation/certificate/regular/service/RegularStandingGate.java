package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.StandingBasis;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingNotConfirmedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RegularStandingGate {

    private static final int EVIDENCE_MIN_LENGTH = 10;
    private static final int EVIDENCE_MAX_LENGTH = 500;

    private final BillingModeGate billingModeGate;
    private final OverdueDebtProbe overdueDebtProbe;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public RegularStandingGate(BillingModeGate billingModeGate, OverdueDebtProbe overdueDebtProbe,
                               CurrentActorFinder currentActorFinder, Clock clock) {
        this.billingModeGate = billingModeGate;
        this.overdueDebtProbe = overdueDebtProbe;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    public StandingResolution resolve(Long studentId, RegularityKind kind, Long enrollmentId, StandingConfirmation confirmation) {
        BillingMode mode = billingModeGate.currentMode();
        if (mode == BillingMode.FULL) {
            return resolveFromBilling(studentId, kind, enrollmentId, mode);
        }
        return resolveFromManualConfirmation(mode, confirmation);
    }

    private StandingResolution resolveFromBilling(Long studentId, RegularityKind kind, Long enrollmentId, BillingMode mode) {
        if (!overdueDebtProbe.isEnrollmentFeePaid(kind, enrollmentId) || overdueDebtProbe.hasOverdueDebt(studentId)) {
            throw new StudentHasDebtException();
        }
        return new StandingResolution(StandingBasis.BILLING_VERIFIED, mode, null, null, null);
    }

    private StandingResolution resolveFromManualConfirmation(BillingMode mode, StandingConfirmation confirmation) {
        if (!confirmation.confirmed()) {
            throw new StandingNotConfirmedException();
        }
        String evidence = confirmation.evidence() == null ? "" : confirmation.evidence().trim();
        if (evidence.length() < EVIDENCE_MIN_LENGTH || evidence.length() > EVIDENCE_MAX_LENGTH) {
            throw new StandingEvidenceInvalidException();
        }
        return new StandingResolution(StandingBasis.MANUALLY_CONFIRMED, mode, currentActorFinder.currentPersonId(),
                LocalDateTime.now(clock), evidence);
    }

    public record StandingConfirmation(boolean confirmed, String evidence) {
    }

    public record StandingResolution(StandingBasis basis, BillingMode mode, Long confirmedById, LocalDateTime confirmedAt,
                                     String evidence) {
    }
}
