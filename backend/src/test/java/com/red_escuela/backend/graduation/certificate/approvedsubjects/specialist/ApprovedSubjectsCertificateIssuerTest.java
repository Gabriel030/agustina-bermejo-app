package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGradesReader;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityInput;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsActor;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsEligibilityFacts;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsRefusals;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnApprovedSubjectsEnrollments;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnedEnrollment;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.render.ApprovedSubjectsPdfRenderer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.red_escuela.backend.shared.module.BillingMode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovedSubjectsCertificateIssuerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private final StudentFinder studentFinder = mock(StudentFinder.class);
    private final DocumentIssuanceSettingsFinder settingsFinder = mock(DocumentIssuanceSettingsFinder.class);
    private final ApprovedSubjectsPdfRenderer renderer = mock(ApprovedSubjectsPdfRenderer.class);
    private final ApprovedSubjectsCertificateRepository repository = mock(ApprovedSubjectsCertificateRepository.class);
    private final VerifiableDocumentRegistry registry = mock(VerifiableDocumentRegistry.class);
    private final ApprovedSubjectsRefusals refusals = mock(ApprovedSubjectsRefusals.class);
    private final ProgramEnrollmentFinder programEnrollmentFinder = mock(ProgramEnrollmentFinder.class);
    private final GraduationSnapshotFactory holderFactory = mock(GraduationSnapshotFactory.class);
    private final ApprovedSubjectsEligibilityFacts eligibilityFacts = mock(ApprovedSubjectsEligibilityFacts.class);
    private final OwnApprovedSubjectsEnrollments ownEnrollments = mock(OwnApprovedSubjectsEnrollments.class);
    private final ApprovedSubjectsActor actor = mock(ApprovedSubjectsActor.class);
    private ApprovedSubjectsCertificateIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new ApprovedSubjectsCertificateIssuer(studentFinder, programEnrollmentFinder,
                mock(ProgramCurriculumFinder.class), mock(ApprovedSubjectGradesReader.class), settingsFinder,
                holderFactory, mock(ApprovedSubjectsSnapshotFactory.class), renderer,
                mock(CertificateDigest.class), repository, registry, mock(AuditTrail.class),
                mock(ApprovedSubjectsCertificateResponseAssembler.class), refusals, new SimpleMeterRegistry(), mock(CertificateTemplateRenderSource.class),
                eligibilityFacts, ownEnrollments, actor, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(studentFinder.requireByIdForUpdate(1L)).thenReturn(mock(Student.class));
        when(settingsFinder.current()).thenReturn(new DocumentIssuanceSettingsResponse(5, 1, 30, 1, 1L));
    }

    @Test
    void dailyLimitReachedRefusesBeforeRenderingOrSaving() {
        when(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(anyLong(), any(), any())).thenReturn(1L);
        when(refusals.issuanceLimitReached()).thenReturn(new IllegalStateException("limit"));

        assertThatThrownBy(() -> issuer.issue(1L, 2L, 3L, NOW)).hasMessage("limit");

        verifyNoInteractions(renderer, registry);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void studentRowIsLockedBeforeAnythingElseIsRead() {
        when(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(anyLong(), any(), any())).thenReturn(1L);
        when(refusals.issuanceLimitReached()).thenReturn(new IllegalStateException("limit"));

        assertThatThrownBy(() -> issuer.issue(1L, 2L, 3L, NOW)).isInstanceOf(IllegalStateException.class);

        var order = org.mockito.Mockito.inOrder(studentFinder, repository);
        order.verify(studentFinder).requireByIdForUpdate(1L);
        order.verify(repository).countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(anyLong(), any(), any());
    }

    @Test
    void codeGenerationGivesUpAfterThreeCollisions() {
        when(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(anyLong(), any(), any())).thenReturn(0L);
        when(repository.existsByCode(anyString())).thenReturn(true);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(enrollment.getProgram()).thenReturn(mock(Program.class));
        when(enrollment.getPlan()).thenReturn(mock(com.red_escuela.backend.academicstructure.entity.CurriculumPlan.class));
        when(programEnrollmentFinder.requireOwnedBy(2L, 1L)).thenReturn(enrollment);
        when(holderFactory.create(any(), anyString(), any(), any(), any(), any())).thenReturn(mock(CertificateSnapshot.class));

        assertThatThrownBy(() -> issuer.issue(1L, 2L, 3L, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("código");

        verify(repository, times(3)).existsByCode(anyString());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(renderer);
    }

    @Test
    void debtExceptionIssuesDespiteOverdueDebtAndRecordsTheStaffActor() {
        ApprovedSubjectsCertificateIssuer spied = spy(issuer);
        ApprovedSubjectsCertificateResponse response = mock(ApprovedSubjectsCertificateResponse.class);
        OwnedEnrollment owned = new OwnedEnrollment(2L, true);
        when(ownEnrollments.findOwnedBy(1L, 2L)).thenReturn(Optional.of(owned));
        when(eligibilityFacts.inputFor(1L, owned, NOW)).thenReturn(new ApprovedSubjectsEligibilityInput(BillingMode.FULL, true, true, 0, 1, 3, true));
        when(actor.currentPersonId()).thenReturn(9L);
        doReturn(response).when(spied).issue(1L, 2L, 9L, NOW);

        assertThat(spied.issueUnderDebtException(1L, 2L)).isSameAs(response);
    }

    @Test
    void debtExceptionStillRefusesAStudentWhoIsNotRegular() {
        ApprovedSubjectsCertificateIssuer spied = spy(issuer);
        OwnedEnrollment owned = new OwnedEnrollment(2L, true);
        when(ownEnrollments.findOwnedBy(1L, 2L)).thenReturn(Optional.of(owned));
        when(eligibilityFacts.inputFor(1L, owned, NOW)).thenReturn(new ApprovedSubjectsEligibilityInput(BillingMode.FULL, true, false, 0, 1, 3, true));
        when(refusals.notEligible()).thenReturn(new IllegalStateException("not eligible"));

        assertThatThrownBy(() -> spied.issueUnderDebtException(1L, 2L)).hasMessage("not eligible");

        verify(spied, never()).issue(anyLong(), anyLong(), any(), any());
    }

    @Test
    void debtExceptionStillRefusesWhenThereAreNoApprovedSubjects() {
        OwnedEnrollment owned = new OwnedEnrollment(2L, true);
        when(ownEnrollments.findOwnedBy(1L, 2L)).thenReturn(Optional.of(owned));
        when(eligibilityFacts.inputFor(1L, owned, NOW)).thenReturn(new ApprovedSubjectsEligibilityInput(BillingMode.OFF, false, true, 0, 1, 0, true));
        when(refusals.notEligible()).thenReturn(new IllegalStateException("not eligible"));

        assertThatThrownBy(() -> issuer.issueUnderDebtException(1L, 2L)).hasMessage("not eligible");
    }

    @Test
    void debtExceptionStillAppliesTheDailyLimit() {
        OwnedEnrollment owned = new OwnedEnrollment(2L, true);
        when(ownEnrollments.findOwnedBy(1L, 2L)).thenReturn(Optional.of(owned));
        when(eligibilityFacts.inputFor(1L, owned, NOW)).thenReturn(new ApprovedSubjectsEligibilityInput(BillingMode.FULL, true, true, 1, 1, 3, true));
        when(refusals.issuanceLimitReached()).thenReturn(new IllegalStateException("limit"));

        assertThatThrownBy(() -> issuer.issueUnderDebtException(1L, 2L)).hasMessage("limit");
    }

    @Test
    void debtExceptionRefusesAnEnrollmentOfAnotherStudent() {
        when(ownEnrollments.findOwnedBy(1L, 2L)).thenReturn(Optional.empty());
        when(refusals.enrollmentNotFound()).thenReturn(new IllegalStateException("not found"));

        assertThatThrownBy(() -> issuer.issueUnderDebtException(1L, 2L)).hasMessage("not found");

        verifyNoInteractions(renderer, registry);
    }
}
