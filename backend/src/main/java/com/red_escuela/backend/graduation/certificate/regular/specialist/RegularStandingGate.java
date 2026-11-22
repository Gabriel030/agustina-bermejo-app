package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.StandingBasis;
import com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceRequiredException;
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

    private static final int EVIDENCE_MAX_LENGTH = 500;

    private final BillingModeGate billingModeGate;
    private final OverdueDebtProbe overdueDebtProbe;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;
    private final RegularCertificateSettingsFinder settingsFinder;

    public RegularStandingGate(BillingModeGate billingModeGate, OverdueDebtProbe overdueDebtProbe,
                               CurrentActorFinder currentActorFinder, Clock clock,
                               RegularCertificateSettingsFinder settingsFinder) {
        this.billingModeGate = billingModeGate;
        this.overdueDebtProbe = overdueDebtProbe;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
        this.settingsFinder = settingsFinder;
    }

    public boolean requiresStaffConfirmation() {
        return billingModeGate.currentMode() != BillingMode.FULL;
    }

    public StandingResolution resolve(Long studentId, RegularityKind kind, Long enrollmentId, StandingConfirmation confirmation) {
        BillingMode mode = billingModeGate.currentMode();
        if (mode == BillingMode.FULL) {
            return resolveFromBilling(studentId, kind, enrollmentId, mode);
        }
        return resolveFromManualConfirmation(mode, confirmation);
    }

    public StandingResolution resolveForSelfIssue(Long studentId, RegularityKind kind, Long enrollmentId) {
        BillingMode mode = billingModeGate.currentMode();
        if (mode == BillingMode.FULL) {
            return resolveFromBilling(studentId, kind, enrollmentId, mode);
        }
        return new StandingResolution(StandingBasis.BILLING_NOT_ENFORCED, mode, null, null, null);
    }

    public void requireDebtToExcept(Long studentId, RegularityKind kind, Long enrollmentId) {
        if (billingModeGate.currentMode() == BillingMode.FULL && isSettled(studentId, kind, enrollmentId)) {
            throw new NoDebtToExceptException();
        }
    }

    public StandingResolution resolveByException(Long studentId, RegularityKind kind, Long enrollmentId) {
        requireDebtToExcept(studentId, kind, enrollmentId);
        return new StandingResolution(StandingBasis.EXCEPTION_APPROVED, billingModeGate.currentMode(), null, null, null);
    }

    private boolean isSettled(Long studentId, RegularityKind kind, Long enrollmentId) {
        return overdueDebtProbe.isEnrollmentFeePaid(kind, enrollmentId) && !overdueDebtProbe.hasOverdueDebt(studentId);
    }

    private StandingResolution resolveFromBilling(Long studentId, RegularityKind kind, Long enrollmentId, BillingMode mode) {
        if (!isSettled(studentId, kind, enrollmentId)) {
            throw new StudentHasDebtException();
        }
        return new StandingResolution(StandingBasis.BILLING_VERIFIED, mode, null, null, null);
    }

    private StandingResolution resolveFromManualConfirmation(BillingMode mode, StandingConfirmation confirmation) {
        if (!confirmation.confirmed()) {
            throw new StandingNotConfirmedException();
        }
        String trimmed = confirmation.evidence() == null ? "" : confirmation.evidence().trim();
        if (trimmed.length() > EVIDENCE_MAX_LENGTH) {
            throw new StandingEvidenceInvalidException();
        }
        if (trimmed.isEmpty() && settingsFinder.current().evidenceRequired()) {
            throw new StandingEvidenceRequiredException();
        }
        String evidence = trimmed.isEmpty() ? null : trimmed;
        return new StandingResolution(StandingBasis.MANUALLY_CONFIRMED, mode, currentActorFinder.currentPersonId(),
                LocalDateTime.now(clock), evidence);
    }

    public record StandingConfirmation(boolean confirmed, String evidence) {
    }

    public record StandingResolution(StandingBasis basis, BillingMode mode, Long confirmedById, LocalDateTime confirmedAt,
                                     String evidence) {
    }
}
