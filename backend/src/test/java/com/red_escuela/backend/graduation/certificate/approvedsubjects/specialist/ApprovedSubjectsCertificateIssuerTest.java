package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGradesReader;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsRefusals;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.render.ApprovedSubjectsPdfRenderer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private ApprovedSubjectsCertificateIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new ApprovedSubjectsCertificateIssuer(studentFinder, programEnrollmentFinder,
                mock(ProgramCurriculumFinder.class), mock(ApprovedSubjectGradesReader.class), settingsFinder,
                holderFactory, mock(ApprovedSubjectsSnapshotFactory.class), renderer,
                mock(CertificateDigest.class), repository, registry, mock(AuditTrail.class),
                mock(ApprovedSubjectsCertificateResponseAssembler.class), refusals, new SimpleMeterRegistry());
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
        when(programEnrollmentFinder.requireOwnedBy(2L, 1L)).thenReturn(enrollment);
        when(holderFactory.create(any(), anyString(), any(), any(), any(), any())).thenReturn(mock(CertificateSnapshot.class));

        assertThatThrownBy(() -> issuer.issue(1L, 2L, 3L, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("código");

        verify(repository, times(3)).existsByCode(anyString());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(renderer);
    }
}
