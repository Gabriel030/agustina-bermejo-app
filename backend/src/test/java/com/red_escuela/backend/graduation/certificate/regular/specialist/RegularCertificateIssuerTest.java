package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionAlreadyConsumedException;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.StandingBasis;
import com.red_escuela.backend.graduation.certificate.regular.exception.ConcurrentRegularCertificateIssueException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NotRegularException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularEnrollmentNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingNotConfirmedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentDocumentMissingException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate.StandingResolution;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.events.RegularCertificateIssuedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.module.BillingMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegularCertificateIssuerTest {

    private static final Long ENROLLMENT_ID = 17L;
    private static final Long STUDENT_ID = 5L;
    private static final Long ACTOR_ID = 9L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private final RegularStudentCertificateRepository certificateRepository = mock(RegularStudentCertificateRepository.class);
    private final RegularCertificateRequestRepository requestRepository = mock(RegularCertificateRequestRepository.class);
    private final RegularityEvaluator regularityEvaluator = mock(RegularityEvaluator.class);
    private final RegularStandingGate standingGate = mock(RegularStandingGate.class);
    private final RegularEnrollmentFactsFinder factsFinder = mock(RegularEnrollmentFactsFinder.class);
    private final RegularCertificateTemplate template = mock(RegularCertificateTemplate.class);
    private final CertificatePdfRenderer renderer = mock(CertificatePdfRenderer.class);
    private final CertificateFileStore fileStore = mock(CertificateFileStore.class);
    private final CertificateVerificationUrl verificationUrl = mock(CertificateVerificationUrl.class);
    private final CurrentActorFinder currentActorFinder = mock(CurrentActorFinder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final CertificateExceptionConsumer exceptionConsumer = mock(CertificateExceptionConsumer.class);
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final IssueRegularCertificateCommand command = new IssueRegularCertificateCommand(RegularityKind.PROGRAM,
            ENROLLMENT_ID, true, "Recibo 1234 en el sistema contable", RegularCertificateChannel.STAFF, null, null);
    private final CertificateSnapshot snapshot = new CertificateSnapshot("PEREZ, ANA", "DNI", "30111222", "la carrera",
            "Tecnicatura", null, null, NOW.toLocalDate(), NOW.toLocalDate(), "Instituto", List.of(),
            RegularCertificateTemplate.REGULAR_BUILT_IN_V1, null);
    private final StandingResolution manualResolution = new StandingResolution(StandingBasis.MANUALLY_CONFIRMED, BillingMode.OFF,
            ACTOR_ID, NOW, "Recibo 1234 en el sistema contable");
    private final byte[] pdf = {1, 2, 3};

    private RegularCertificateSettingsFinder settingsFinderOf(int validityDays) {
        RegularCertificateSettingsFinder finder = mock(RegularCertificateSettingsFinder.class);
        when(finder.current()).thenReturn(RegularCertificateSettings.of(validityDays, 7, false));
        return finder;
    }

    private RegularCertificateIssuer issuer(int validityDays) {
        return new RegularCertificateIssuer(certificateRepository, requestRepository, regularityEvaluator, standingGate,
                factsFinder, template, renderer, fileStore, verificationUrl, new CertificateDigest(), currentActorFinder,
                eventPublisher, new SimpleMeterRegistry(), clock, settingsFinderOf(validityDays), exceptionConsumer,
                mock(VerifiableDocumentRegistry.class), mock(CertificateTemplateRenderSource.class));
    }

    @BeforeEach
    void givenRegularStudentReadyToIssue() {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(STUDENT_ID);
        Person person = mock(Person.class);
        when(person.getName()).thenReturn("Ana");
        when(person.getLastname()).thenReturn("Perez");
        when(person.getDni()).thenReturn("30111222");
        when(student.getPerson()).thenReturn(person);
        when(factsFinder.find(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(new RegularEnrollmentFacts(student, "la carrera", "Tecnicatura"));
        when(currentActorFinder.currentPersonId()).thenReturn(ACTOR_ID);
        when(regularityEvaluator.evaluate(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(verdict(true, List.of()));
        when(standingGate.resolve(eq(STUDENT_ID), eq(RegularityKind.PROGRAM), eq(ENROLLMENT_ID), any())).thenReturn(manualResolution);
        when(template.snapshotOf(any(), any())).thenReturn(snapshot);
        when(template.definitionFor(any(), any())).thenReturn(mock(CertificateTemplateDefinition.class));
        when(verificationUrl.of(any())).thenReturn("https://app.example/verificar/X");
        when(renderer.render(any(), any(), any(), anyString())).thenReturn(pdf);
        when(fileStore.save(anyString(), any())).thenReturn("certificates/x.pdf");
        when(certificateRepository.findByProgramEnrollmentIdAndStatus(ENROLLMENT_ID, RegularCertificateStatus.VALID)).thenReturn(Optional.empty());
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(certificateRepository.saveAndFlush(any(RegularStudentCertificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RegularityVerdict verdict(boolean regular, List<RegularityReason> reasons) {
        return new RegularityVerdict(RegularityKind.PROGRAM, regular, reasons, true, Instant.parse("2026-11-05T12:00:00Z"));
    }

    private RegularCertificateRequest capturedRequest() {
        ArgumentCaptor<RegularCertificateRequest> captor = ArgumentCaptor.forClass(RegularCertificateRequest.class);
        verify(requestRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void studentWithoutDocumentIsRejectedBeforeRenderingAndNothingIsStored() {
        Student student = mock(Student.class);
        Person person = mock(Person.class);
        when(person.getDni()).thenReturn(null);
        when(student.getPerson()).thenReturn(person);
        when(factsFinder.find(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(new RegularEnrollmentFacts(student, "la carrera", "Tecnicatura"));

        assertThatThrownBy(() -> issuer(30).issue(command)).isInstanceOf(StudentDocumentMissingException.class);
        assertThatThrownBy(() -> issuer(30).submitFromPortal(command)).isInstanceOf(StudentDocumentMissingException.class);

        verify(renderer, never()).render(any(), any(), any(), anyString());
        verify(fileStore, never()).save(anyString(), any());
        verify(certificateRepository, never()).saveAndFlush(any(RegularStudentCertificate.class));
    }

    @Test
    void notRegularStudentIsRejectedAndRequestIsRecorded() {
        when(regularityEvaluator.evaluate(RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenReturn(verdict(false, List.of(RegularityReason.FREE_IN_ACTIVE_SECTION)));

        assertThatThrownBy(() -> issuer(30).issue(command)).isInstanceOf(NotRegularException.class);

        RegularCertificateRequest request = capturedRequest();
        assertThat(request.getOutcome()).isEqualTo(RegularRequestOutcome.NOT_REGULAR);
        assertThat(request.getReasons()).containsExactly("FREE_IN_ACTIVE_SECTION");
        verify(standingGate, never()).resolve(anyLong(), any(), anyLong(), any());
        verify(certificateRepository, never()).saveAndFlush(any(RegularStudentCertificate.class));
        verify(fileStore, never()).save(anyString(), any());
    }

    @Test
    void blockedStandingIsRecordedAsRejectedWithItsReason() {
        when(standingGate.resolve(eq(STUDENT_ID), eq(RegularityKind.PROGRAM), eq(ENROLLMENT_ID), any()))
                .thenThrow(new StandingNotConfirmedException());

        assertThatThrownBy(() -> issuer(30).issue(command)).isInstanceOf(StandingNotConfirmedException.class);

        RegularCertificateRequest request = capturedRequest();
        assertThat(request.getOutcome()).isEqualTo(RegularRequestOutcome.REJECTED);
        assertThat(request.getReasons()).containsExactly("STANDING_NOT_CONFIRMED");
        verify(fileStore, never()).save(anyString(), any());
    }

    @Test
    void expiresAtIsIssuedAtPlusValidityDays() {
        RegularCertificateResponse response = issuer(30).issue(command);

        assertThat(response.issuedAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusDays(30));
        assertThat(response.standingBasis()).isEqualTo(StandingBasis.MANUALLY_CONFIRMED);
        assertThat(response.billingModeAtIssue()).isEqualTo("OFF");
        assertThat(response.status()).isEqualTo(RegularCertificateStatus.VALID);
        assertThat(response.enrollmentId()).isEqualTo(ENROLLMENT_ID);
        verify(template).definitionFor(any(), eq(LocalDate.of(2026, 12, 5)));
    }

    @Test
    void certificateStoresValidityDaysOfSettingsAtIssue() {
        issuer(45).issue(command);

        ArgumentCaptor<RegularStudentCertificate> saved = ArgumentCaptor.forClass(RegularStudentCertificate.class);
        verify(certificateRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getValidityDays()).isEqualTo(45);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusDays(45));
    }

    @Test
    void existingValidCertificateReturnsAlreadyIssued() {
        RegularStudentCertificate current = valid(NOW.plusDays(3));
        when(certificateRepository.findByProgramEnrollmentIdAndStatus(ENROLLMENT_ID, RegularCertificateStatus.VALID))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> issuer(30).issue(command)).isInstanceOf(RegularCertificateAlreadyIssuedException.class);

        assertThat(capturedRequest().getOutcome()).isEqualTo(RegularRequestOutcome.ALREADY_ISSUED);
        assertThat(current.getStatus()).isEqualTo(RegularCertificateStatus.VALID);
        verify(regularityEvaluator, never()).evaluate(any(), anyLong());
    }

    @Test
    void expiredCertificateIsReplacedByNewIssue() {
        RegularStudentCertificate expired = valid(NOW.minusMinutes(1));
        when(certificateRepository.findByProgramEnrollmentIdAndStatus(ENROLLMENT_ID, RegularCertificateStatus.VALID))
                .thenReturn(Optional.of(expired));

        RegularCertificateResponse response = issuer(30).issue(command);

        assertThat(expired.getStatus()).isEqualTo(RegularCertificateStatus.EXPIRED);
        assertThat(response.status()).isEqualTo(RegularCertificateStatus.VALID);
        verify(certificateRepository).saveAndFlush(expired);
    }

    @Test
    void certificateExpiringExactlyNowStillBlocksANewIssue() {
        when(certificateRepository.findByProgramEnrollmentIdAndStatus(ENROLLMENT_ID, RegularCertificateStatus.VALID))
                .thenReturn(Optional.of(valid(NOW)));

        assertThatThrownBy(() -> issuer(30).issue(command)).isInstanceOf(RegularCertificateAlreadyIssuedException.class);
    }

    @Test
    void snapshotKeepsStudentDataAtIssueTime() {
        issuer(30).issue(command);

        ArgumentCaptor<RegularStudentCertificate> captor = ArgumentCaptor.forClass(RegularStudentCertificate.class);
        verify(certificateRepository).saveAndFlush(captor.capture());
        RegularStudentCertificate saved = captor.getValue();
        assertThat(saved.getSnapshot()).isSameAs(snapshot);
        assertThat(saved.getTemplateRef()).isEqualTo(RegularCertificateTemplate.REGULAR_BUILT_IN_V1);
        assertThat(saved.getStandingEvidence()).isEqualTo("Recibo 1234 en el sistema contable");
        assertThat(saved.getStandingConfirmedById()).isEqualTo(ACTOR_ID);
        assertThat(saved.getPdfSha256()).hasSize(64);
        assertThat(saved.getProgramEnrollmentId()).isEqualTo(ENROLLMENT_ID);
        assertThat(saved.getCourseEnrollmentId()).isNull();
    }

    @Test
    void issuedRequestIsLinkedToTheCertificateAndAnEventIsPublished() {
        when(certificateRepository.saveAndFlush(any(RegularStudentCertificate.class))).thenAnswer(invocation -> {
            RegularStudentCertificate certificate = invocation.getArgument(0);
            return RegularStudentCertificate.builder().id(77L).code(certificate.getCode()).kind(certificate.getKind())
                    .programEnrollmentId(certificate.getProgramEnrollmentId()).status(certificate.getStatus())
                    .issuedAt(certificate.getIssuedAt()).expiresAt(certificate.getExpiresAt()).standingBasis(certificate.getStandingBasis())
                    .billingModeAtIssue(certificate.getBillingModeAtIssue()).studentId(certificate.getStudentId())
                    .snapshot(certificate.getSnapshot()).build();
        });

        issuer(30).issue(command);

        ArgumentCaptor<RegularCertificateRequest> captor = ArgumentCaptor.forClass(RegularCertificateRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getCertificateId()).isEqualTo(77L);
        assertThat(captor.getValue().getOutcome()).isEqualTo(RegularRequestOutcome.ISSUED);
        ArgumentCaptor<RegularCertificateIssuedEvent> event = ArgumentCaptor.forClass(RegularCertificateIssuedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().certificateId()).isEqualTo(77L);
        assertThat(event.getValue().studentId()).isEqualTo(STUDENT_ID);
    }

    @Test
    void exceptionIssuanceUsesTheExceptionStandingLinksItAndConsumesItWithTheCertificateId() {
        IssueRegularCertificateCommand withException = new IssueRegularCertificateCommand(RegularityKind.PROGRAM, ENROLLMENT_ID, false, null,
                RegularCertificateChannel.STAFF, 55L, null);
        when(standingGate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenReturn(new StandingResolution(StandingBasis.EXCEPTION_APPROVED, BillingMode.FULL, null, null, null));
        when(certificateRepository.saveAndFlush(any(RegularStudentCertificate.class))).thenAnswer(invocation -> {
            RegularStudentCertificate certificate = invocation.getArgument(0);
            assertThat(certificate.getStandingBasis()).isEqualTo(StandingBasis.EXCEPTION_APPROVED);
            assertThat(certificate.getExceptionId()).isEqualTo(55L);
            return RegularStudentCertificate.builder().id(77L).code(certificate.getCode()).kind(certificate.getKind())
                    .studentId(certificate.getStudentId()).status(certificate.getStatus()).issuedAt(certificate.getIssuedAt())
                    .expiresAt(certificate.getExpiresAt()).standingBasis(certificate.getStandingBasis())
                    .billingModeAtIssue(certificate.getBillingModeAtIssue()).snapshot(certificate.getSnapshot())
                    .programEnrollmentId(certificate.getProgramEnrollmentId()).build();
        });

        issuer(30).issue(withException);

        verify(exceptionConsumer).consume(55L, 77L, STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID, ACTOR_ID);
        verify(standingGate, never()).resolve(any(), any(), any(), any());
    }

    @Test
    void failingExceptionConsumptionRemovesTheStoredFileAndPropagates() {
        IssueRegularCertificateCommand withException = new IssueRegularCertificateCommand(RegularityKind.PROGRAM, ENROLLMENT_ID, false, null,
                RegularCertificateChannel.STAFF, 55L, null);
        when(standingGate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenReturn(new StandingResolution(StandingBasis.EXCEPTION_APPROVED, BillingMode.FULL, null, null, null));
        doThrow(new ExceptionAlreadyConsumedException()).when(exceptionConsumer).consume(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> issuer(30).issue(withException)).isInstanceOf(ExceptionAlreadyConsumedException.class);

        verify(fileStore).deleteIfRolledBack("certificates/x.pdf");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void issuanceWithoutExceptionNeverTouchesTheConsumer() {
        issuer(30).issue(command);

        verify(exceptionConsumer, never()).consume(any(), any(), any(), any(), any(), any());
    }

    @Test
    void debtExceptionIssuanceUsesExceptionStandingStaffChannelAndNeverTouchesTheLegacyConsumer() {
        when(standingGate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenReturn(new StandingResolution(StandingBasis.EXCEPTION_APPROVED, BillingMode.FULL, null, null, null));
        when(certificateRepository.saveAndFlush(any(RegularStudentCertificate.class))).thenAnswer(invocation -> {
            RegularStudentCertificate certificate = invocation.getArgument(0);
            assertThat(certificate.getStandingBasis()).isEqualTo(StandingBasis.EXCEPTION_APPROVED);
            assertThat(certificate.getExceptionId()).isNull();
            assertThat(certificate.getIssueChannel()).isEqualTo(RegularCertificateChannel.STAFF);
            assertThat(certificate.getIssuedById()).isEqualTo(ACTOR_ID);
            return certificate;
        });

        issuer(30).issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID);

        verify(standingGate, never()).resolve(any(), any(), any(), any());
        verify(exceptionConsumer, never()).consume(any(), any(), any(), any(), any(), any());
    }

    @Test
    void debtExceptionIssuanceRejectsAnEnrollmentOfAnotherStudentBeforeStoringAnything() {
        when(factsFinder.findOwnedBy(RegularityKind.PROGRAM, ENROLLMENT_ID, 999L)).thenThrow(new RegularEnrollmentNotFoundException());

        assertThatThrownBy(() -> issuer(30).issueUnderDebtException(999L, ENROLLMENT_ID))
                .isInstanceOf(RegularEnrollmentNotFoundException.class);

        verify(certificateRepository, never()).saveAndFlush(any(RegularStudentCertificate.class));
        verify(fileStore, never()).save(anyString(), any());
    }

    @Test
    void debtExceptionIssuanceStillRequiresInstitutionalRegularity() {
        when(regularityEvaluator.evaluate(RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenReturn(verdict(false, List.of(RegularityReason.values()[0])));

        assertThatThrownBy(() -> issuer(30).issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID)).isInstanceOf(NotRegularException.class);

        verify(standingGate, never()).resolveByException(any(), any(), any());
        verify(certificateRepository, never()).saveAndFlush(any(RegularStudentCertificate.class));
    }

    @Test
    void debtExceptionIssuanceWithoutDebtRecordsARejectionAndStoresNothing() {
        when(standingGate.resolveByException(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenThrow(new com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException());

        assertThatThrownBy(() -> issuer(30).issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID))
                .isInstanceOf(com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException.class);

        assertThat(capturedRequest().getOutcome()).isEqualTo(RegularRequestOutcome.REJECTED);
        verify(certificateRepository, never()).saveAndFlush(any(RegularStudentCertificate.class));
    }

    @Test
    void pendingRequestConflictIsReportedAsConcurrentIssue() {
        when(standingGate.requiresStaffConfirmation()).thenReturn(true);
        when(requestRepository.findFirstByProgramEnrollmentIdAndOutcomeOrderByRequestedAtDesc(ENROLLMENT_ID, RegularRequestOutcome.PENDING_STAFF))
                .thenReturn(Optional.empty());
        when(requestRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_regular_cert_request_pending_per_enrollment"));

        assertThatThrownBy(() -> issuer(30).submitFromPortal(command)).isInstanceOf(ConcurrentRegularCertificateIssueException.class);
    }

    @Test
    void uniqueIndexConflictRemovesTheStoredFileAndReportsConcurrentIssue() {
        when(certificateRepository.saveAndFlush(any(RegularStudentCertificate.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> issuer(30).issue(command)).isInstanceOf(ConcurrentRegularCertificateIssueException.class);

        verify(fileStore).deleteIfRolledBack("certificates/x.pdf");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void issueKeepsRecordedRejectionsButRollsBackOnConcurrentConflict() throws NoSuchMethodException {
        Transactional transactional = RegularCertificateIssuer.class.getMethod("issue", IssueRegularCertificateCommand.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.noRollbackFor()).containsExactlyInAnyOrder(NotRegularException.class,
                RegularCertificateAlreadyIssuedException.class, com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException.class,
                StandingNotConfirmedException.class, com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceInvalidException.class,
                com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceRequiredException.class,
                com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException.class);
        assertThat(transactional.noRollbackFor()).doesNotContain(ConcurrentRegularCertificateIssueException.class);
    }

    private RegularStudentCertificate valid(LocalDateTime expiresAt) {
        return RegularStudentCertificate.builder().status(RegularCertificateStatus.VALID).expiresAt(expiresAt)
                .kind(RegularityKind.PROGRAM).programEnrollmentId(ENROLLMENT_ID).build();
    }
}
