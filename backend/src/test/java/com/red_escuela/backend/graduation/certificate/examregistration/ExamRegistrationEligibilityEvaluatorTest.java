package com.red_escuela.backend.graduation.certificate.examregistration;

import com.red_escuela.backend.evaluation.api.OwnExamRegistrationFinder;
import com.red_escuela.backend.evaluation.api.OwnExamRegistrationView;
import com.red_escuela.backend.graduation.api.DocumentIssuanceSettings;
import com.red_escuela.backend.graduation.api.DocumentIssuanceSettings.DocumentIssuanceValues;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.certificate.examregistration.repository.ExamRegistrationCertificateRepository;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationEligibilityEvaluator;
import com.red_escuela.backend.graduation.certificate.regular.specialist.OverdueDebtProbe;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamRegistrationEligibilityEvaluatorTest {

    private static final Long STUDENT_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 10, 0);

    private final OwnExamRegistrationFinder finder = mock(OwnExamRegistrationFinder.class);
    private final ExamRegistrationCertificateRepository repository = mock(ExamRegistrationCertificateRepository.class);
    private final DocumentIssuanceSettings settings = mock(DocumentIssuanceSettings.class);
    private final BillingModeGate billingModeGate = mock(BillingModeGate.class);
    private final OverdueDebtProbe debtProbe = mock(OverdueDebtProbe.class);
    private final CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
    private ExamRegistrationEligibilityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        when(actorFinder.currentStudentId()).thenReturn(STUDENT_ID);
        when(settings.current()).thenReturn(new DocumentIssuanceValues(15, 30, 30, 1));
        when(billingModeGate.currentMode()).thenReturn(BillingMode.FULL);
        when(debtProbe.hasOverdueDebt(anyLong())).thenReturn(false);
        when(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(anyLong(), any(), any())).thenReturn(0L);
        when(repository.findFirstByStudentIdAndRegistrationIdAndStatusOrderByIssuedAtDesc(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        evaluator = new ExamRegistrationEligibilityEvaluator(finder, repository, settings, billingModeGate, debtProbe,
                actorFinder, clock);
    }

    private void registrationOn(LocalDateTime examDateTime) {
        when(finder.activeRegistrationsOf(anyLong(), any())).thenReturn(List.of(view(100L, examDateTime)));
    }

    private OwnExamRegistrationView view(Long registrationId, LocalDateTime examDateTime) {
        return new OwnExamRegistrationView(registrationId, 50L, STUDENT_ID, "Tecnicatura", "Matemática", examDateTime, "Aula 3");
    }

    @Test
    void studentWithoutRegistrationIsNotEligibleAndToldToRegister() {
        when(finder.activeRegistrationsOf(anyLong(), any())).thenReturn(List.of());

        ProcedureEligibilityResponse response = evaluator.evaluate(null);

        assertThat(response.eligible()).isFalse();
        assertThat(response.reasons()).extracting(reason -> reason.code())
                .containsExactly(ProcedureIneligibilityCode.NOT_REGISTERED_FOR_EXAM);
    }

    @Test
    void examSixteenDaysAwayIsNotYetEnabledAndStatesTheOpeningDate() {
        registrationOn(NOW.plusDays(16));

        ProcedureEligibilityResponse response = evaluator.evaluate(null);

        assertThat(response.eligible()).isFalse();
        assertThat(response.reasons().get(0).code()).isEqualTo(ProcedureIneligibilityCode.NO_OPEN_ENROLLMENT_WINDOW);
        assertThat(response.reasons().get(0).message()).isEqualTo("Se habilita 15 días antes: desde el 02/12/2026");
    }

    @Test
    void examExactlyFifteenDaysAwayIsEnabled() {
        registrationOn(NOW.plusDays(15));

        assertThat(evaluator.evaluate(null).eligible()).isTrue();
    }

    @Test
    void examLaterTodayIsStillEnabled() {
        registrationOn(NOW.plusHours(1));

        assertThat(evaluator.evaluate(null).eligible()).isTrue();
    }

    @Test
    void overdueDebtBlocksWithPendingPayments() {
        registrationOn(NOW.plusDays(3));
        when(debtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(true);

        ProcedureEligibilityResponse response = evaluator.evaluate(null);

        assertThat(response.reasons().get(0).code()).isEqualTo(ProcedureIneligibilityCode.PENDING_PAYMENTS);
        assertThat(response.reasons().get(0).message()).doesNotContain("$");
    }

    @Test
    void mirrorModeNeverIssuesByItselfAndSendsToAdministration() {
        registrationOn(NOW.plusDays(3));
        when(billingModeGate.currentMode()).thenReturn(BillingMode.MIRROR);

        ProcedureEligibilityResponse response = evaluator.evaluate(null);

        assertThat(response.eligible()).isFalse();
        assertThat(response.reasons().get(0).message()).contains("comunicate con Administración");
    }

    @Test
    void billingOffSkipsThePaymentsCheckEvenWithDebt() {
        registrationOn(NOW.plusDays(3));
        when(billingModeGate.currentMode()).thenReturn(BillingMode.OFF);
        when(debtProbe.hasOverdueDebt(STUDENT_ID)).thenReturn(true);

        assertThat(evaluator.evaluate(null).eligible()).isTrue();
    }

    @Test
    void dailyLimitReachedBlocksWhenThereIsNoValidCertificateToRedownload() {
        registrationOn(NOW.plusDays(3));
        when(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(anyLong(), any(), any())).thenReturn(1L);

        assertThat(evaluator.evaluate(null).reasons().get(0).code()).isEqualTo(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED);
    }

    @Test
    void dailyLimitDoesNotBlockRedownloadOfTheCurrentValidCertificate() {
        registrationOn(NOW.plusDays(3));
        when(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(anyLong(), any(), any())).thenReturn(1L);
        ExamRegistrationCertificate current = ExamRegistrationCertificate.builder().status(DocumentValidityStatus.VALID)
                .expiresAt(NOW.plusDays(3)).build();
        when(repository.findFirstByStudentIdAndRegistrationIdAndStatusOrderByIssuedAtDesc(STUDENT_ID, 100L, DocumentValidityStatus.VALID))
                .thenReturn(Optional.of(current));

        assertThat(evaluator.evaluate(null).eligible()).isTrue();
    }

    @Test
    void registrationOfAnotherStudentIsTreatedAsNotFound() {
        when(finder.activeRegistrationsOf(anyLong(), any())).thenReturn(List.of(view(100L, NOW.plusDays(3))));

        assertThatThrownBy(() -> evaluator.evaluate(999L)).isInstanceOf(ProcedureDocumentNotFoundException.class);
    }

    @Test
    void defaultPicksTheRegistrationAlreadyInsideTheWindowOverAnEarlierNotYetEnabledOne() {
        when(finder.activeRegistrationsOf(anyLong(), any())).thenReturn(List.of(view(1L, NOW.plusDays(40)), view(2L, NOW.plusDays(5))));

        ProcedureEligibilityResponse response = evaluator.evaluate(null);

        assertThat(response.eligible()).isTrue();
    }
}
