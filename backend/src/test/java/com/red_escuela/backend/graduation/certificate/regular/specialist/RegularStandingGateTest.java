package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.StandingBasis;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceRequiredException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingNotConfirmedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate.StandingConfirmation;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate.StandingResolution;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegularStandingGateTest {

    private static final Long STUDENT_ID = 5L;
    private static final Long ENROLLMENT_ID = 17L;
    private static final StandingConfirmation NOT_CONFIRMED = new StandingConfirmation(false, null);

    private final BillingModeGate billingModeGate = mock(BillingModeGate.class);
    private final OverdueDebtProbe overdueDebtProbe = mock(OverdueDebtProbe.class);
    private final CurrentActorFinder currentActorFinder = mock(CurrentActorFinder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final RegularCertificateSettingsFinder settingsFinder = mock(RegularCertificateSettingsFinder.class);
    private final RegularStandingGate gate = new RegularStandingGate(billingModeGate, overdueDebtProbe, currentActorFinder, clock,
            settingsFinder);

    {
        when(settingsFinder.current()).thenReturn(RegularCertificateSettings.DEFAULT);
    }

    private void givenMode(BillingMode mode) {
        when(billingModeGate.currentMode()).thenReturn(mode);
    }

    private StandingResolution resolve(StandingConfirmation confirmation) {
        return gate.resolve(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID, confirmation);
    }

    @Test
    void fullModeWithoutOverdueDebtReturnsBillingVerified() {
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);
        when(overdueDebtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(false);

        StandingResolution resolution = resolve(NOT_CONFIRMED);

        assertThat(resolution.basis()).isEqualTo(StandingBasis.BILLING_VERIFIED);
        assertThat(resolution.mode()).isEqualTo(BillingMode.FULL);
        assertThat(resolution.confirmedById()).isNull();
        assertThat(resolution.evidence()).isNull();
    }

    @Test
    void fullModeWithOverdueDebtThrowsStudentHasDebt() {
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);
        when(overdueDebtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> resolve(NOT_CONFIRMED)).isInstanceOf(StudentHasDebtException.class);
    }

    @Test
    void fullModeWithUnpaidEnrollmentFeeThrowsStudentHasDebt() {
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(false);
        when(overdueDebtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> resolve(NOT_CONFIRMED)).isInstanceOf(StudentHasDebtException.class);
    }

    @Test
    void fullModeIgnoresManualConfirmation() {
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);
        when(overdueDebtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> resolve(new StandingConfirmation(true, "recibo 1234 verificado")))
                .isInstanceOf(StudentHasDebtException.class);
    }

    @Test
    void offModeWithoutConfirmationThrowsStandingNotConfirmed() {
        givenMode(BillingMode.OFF);

        assertThatThrownBy(() -> resolve(NOT_CONFIRMED)).isInstanceOf(StandingNotConfirmedException.class);
    }

    @Test
    void offModeWithConfirmationAndEvidenceReturnsManuallyConfirmed() {
        givenMode(BillingMode.OFF);
        when(currentActorFinder.currentPersonId()).thenReturn(9L);

        StandingResolution resolution = resolve(new StandingConfirmation(true, "  Recibo 1234 en el sistema contable  "));

        assertThat(resolution.basis()).isEqualTo(StandingBasis.MANUALLY_CONFIRMED);
        assertThat(resolution.mode()).isEqualTo(BillingMode.OFF);
        assertThat(resolution.confirmedById()).isEqualTo(9L);
        assertThat(resolution.confirmedAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 12, 0));
        assertThat(resolution.evidence()).isEqualTo("Recibo 1234 en el sistema contable");
    }

    @Test
    void offModeNeverCallsBilling() {
        givenMode(BillingMode.OFF);
        when(currentActorFinder.currentPersonId()).thenReturn(9L);

        resolve(new StandingConfirmation(true, "Recibo 1234 en el sistema contable"));

        verifyNoInteractions(overdueDebtProbe);
    }

    @Test
    void mirrorModeBehavesAsOff() {
        givenMode(BillingMode.MIRROR);

        assertThatThrownBy(() -> resolve(NOT_CONFIRMED)).isInstanceOf(StandingNotConfirmedException.class);
        verifyNoInteractions(overdueDebtProbe);
    }

    @Test
    void evidenceIsOptionalAndShortOrBlankNotesAreStoredTrimmedOrAsNull() {
        givenMode(BillingMode.OFF);
        when(currentActorFinder.currentPersonId()).thenReturn(9L);

        assertThat(resolve(new StandingConfirmation(true, null)).evidence()).isNull();
        assertThat(resolve(new StandingConfirmation(true, "     ")).evidence()).isNull();
        assertThat(resolve(new StandingConfirmation(true, "  corta  ")).evidence()).isEqualTo("corta");
        assertThat(resolve(new StandingConfirmation(true, null)).basis()).isEqualTo(StandingBasis.MANUALLY_CONFIRMED);
    }

    @Test
    void evidenceUpToFiveHundredCharactersIsAccepted() {
        givenMode(BillingMode.OFF);
        when(currentActorFinder.currentPersonId()).thenReturn(9L);

        assertThat(resolve(new StandingConfirmation(true, "x".repeat(500))).evidence()).hasSize(500);
    }

    @Test
    void evidenceLongerThanFiveHundredCharactersIsRejected() {
        givenMode(BillingMode.OFF);

        assertThatThrownBy(() -> resolve(new StandingConfirmation(true, "x".repeat(501)))).isInstanceOf(StandingEvidenceInvalidException.class);
    }

    @Test
    void manualModeWithoutEvidenceIsRejectedWhenEvidenceIsRequired() {
        when(settingsFinder.current()).thenReturn(RegularCertificateSettings.of(30, 7, true));
        for (BillingMode mode : new BillingMode[]{BillingMode.OFF, BillingMode.MIRROR}) {
            givenMode(mode);
            assertThatThrownBy(() -> resolve(new StandingConfirmation(true, "   "))).isInstanceOf(StandingEvidenceRequiredException.class);
            assertThatThrownBy(() -> resolve(new StandingConfirmation(true, null))).isInstanceOf(StandingEvidenceRequiredException.class);
        }
    }

    @Test
    void manualModeWithEvidenceIsAcceptedWhenEvidenceIsRequired() {
        when(settingsFinder.current()).thenReturn(RegularCertificateSettings.of(30, 7, true));
        givenMode(BillingMode.OFF);

        assertThat(resolve(new StandingConfirmation(true, "recibo 1")).evidence()).isEqualTo("recibo 1");
    }

    @Test
    void manualModeWithoutEvidenceIsAcceptedWhenEvidenceIsOptional() {
        givenMode(BillingMode.MIRROR);

        assertThat(resolve(new StandingConfirmation(true, null)).evidence()).isNull();
    }

    @Test
    void fullModeIgnoresRequiredEvidence() {
        when(settingsFinder.current()).thenReturn(RegularCertificateSettings.of(30, 7, true));
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);

        assertThat(resolve(NOT_CONFIRMED).basis()).isEqualTo(StandingBasis.BILLING_VERIFIED);
    }

    @Test
    void exceptionResolutionInFullModeWithDebtReturnsExceptionApprovedBasis() {
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);
        when(overdueDebtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(true);

        StandingResolution resolution = gate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID);

        assertThat(resolution.basis()).isEqualTo(StandingBasis.EXCEPTION_APPROVED);
        assertThat(resolution.mode()).isEqualTo(BillingMode.FULL);
        assertThat(resolution.confirmedById()).isNull();
    }

    @Test
    void exceptionResolutionInFullModeWithUnpaidEnrollmentFeeCountsAsDebt() {
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(false);
        when(overdueDebtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(false);

        assertThat(gate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID).basis()).isEqualTo(StandingBasis.EXCEPTION_APPROVED);
    }

    @Test
    void exceptionIsRefusedInFullModeWhenThereIsNothingToExcept() {
        givenMode(BillingMode.FULL);
        when(overdueDebtProbe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);
        when(overdueDebtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> gate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID)).isInstanceOf(NoDebtToExceptException.class);
        assertThatThrownBy(() -> gate.requireDebtToExcept(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID)).isInstanceOf(NoDebtToExceptException.class);
    }

    @Test
    void exceptionIsAcceptedWithoutDebtLookupWhenBillingIsNotFull() {
        givenMode(BillingMode.OFF);

        assertThat(gate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID).mode()).isEqualTo(BillingMode.OFF);
        gate.requireDebtToExcept(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID);
        verifyNoInteractions(overdueDebtProbe);
    }
}
