package com.red_escuela.backend.graduation.certificate.exam.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendance;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceView;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendeeView;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationFinder;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationView;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateBoardNotHeldException;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateRegistrationNotEligibleException;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateIssuer.ExamCertificateIssueCommand;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExamCertificateIssuerDebtExceptionTest {

    private static final Long STUDENT_ID = 1L;
    private static final Long REGISTRATION_ID = 2L;
    private static final Long BOARD_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final LocalDate EXAM_DATE = LocalDate.of(2026, 11, 5);

    private final ExamCertificateRepository repository = mock(ExamCertificateRepository.class);
    private final ExamBoardRegistrationFinder registrationFinder = mock(ExamBoardRegistrationFinder.class);
    private final ExamBoardAttendanceReader boardReader = mock(ExamBoardAttendanceReader.class);
    private ExamCertificateIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = spy(new ExamCertificateIssuer(repository, mock(ExamCertificateTemplate.class), mock(CertificatePdfRenderer.class),
                mock(CertificateFileStore.class), mock(CertificateVerificationUrl.class), mock(CertificateDigest.class),
                mock(StudentFinder.class), mock(CurrentActorFinder.class), mock(DocumentIssuanceSettingsFinder.class),
                mock(AuditTrail.class), Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                mock(CertificateTemplateRenderSource.class), registrationFinder, boardReader,
                mock(VerifiableDocumentRegistry.class)));
        when(registrationFinder.requireById(REGISTRATION_ID)).thenReturn(new ExamBoardRegistrationView(REGISTRATION_ID, BOARD_ID));
    }

    @Test
    void boardThatWasNotHeldIsRefused() {
        when(boardReader.attendanceForUpdateOf(BOARD_ID)).thenReturn(board(false, STUDENT_ID, ExamBoardAttendance.PRESENT));

        assertThatThrownBy(() -> issuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID))
                .isInstanceOf(ExamCertificateBoardNotHeldException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void absentStudentIsRefused() {
        when(boardReader.attendanceForUpdateOf(BOARD_ID)).thenReturn(board(true, STUDENT_ID, ExamBoardAttendance.ABSENT));

        assertThatThrownBy(() -> issuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID))
                .isInstanceOf(ExamCertificateRegistrationNotEligibleException.class);
    }

    @Test
    void registrationOfAnotherStudentIsRefused() {
        when(boardReader.attendanceForUpdateOf(BOARD_ID)).thenReturn(board(true, 99L, ExamBoardAttendance.PRESENT));

        assertThatThrownBy(() -> issuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID))
                .isInstanceOf(ExamCertificateRegistrationNotEligibleException.class);
    }

    @Test
    void validCertificateIsReturnedWithoutIssuingAnother() {
        when(boardReader.attendanceForUpdateOf(BOARD_ID)).thenReturn(board(true, STUDENT_ID, ExamBoardAttendance.PRESENT));
        ExamCertificate current = mock(ExamCertificate.class);
        when(current.isExpiredAt(NOW)).thenReturn(false);
        when(repository.findByRegistrationIdAndStatus(REGISTRATION_ID, DocumentValidityStatus.VALID)).thenReturn(Optional.of(current));

        assertThat(issuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID)).isSameAs(current);
        verify(issuer, never()).issue(any());
    }

    @Test
    void expiredCertificateIsReplacedByAStaffIssuedOne() {
        when(boardReader.attendanceForUpdateOf(BOARD_ID)).thenReturn(board(true, STUDENT_ID, ExamBoardAttendance.PRESENT));
        ExamCertificate expired = mock(ExamCertificate.class);
        when(expired.isExpiredAt(NOW)).thenReturn(true);
        when(repository.findByRegistrationIdAndStatus(REGISTRATION_ID, DocumentValidityStatus.VALID)).thenReturn(Optional.of(expired));
        ExamCertificate created = mock(ExamCertificate.class);
        ExamCertificateIssueCommand expected = new ExamCertificateIssueCommand(REGISTRATION_ID, BOARD_ID, STUDENT_ID, "Materia",
                EXAM_DATE, null, null, DocumentOrigin.ISSUED_BY_STAFF);
        doReturn(created).when(issuer).issue(expected);

        assertThat(issuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID)).isSameAs(created);
        verify(expired).expire();
        verify(repository).saveAndFlush(expired);
    }

    private static ExamBoardAttendanceView board(boolean held, Long studentId, ExamBoardAttendance attendance) {
        return new ExamBoardAttendanceView(BOARD_ID, held, EXAM_DATE, "Materia",
                List.of(new ExamBoardAttendeeView(REGISTRATION_ID, studentId, attendance)));
    }
}
