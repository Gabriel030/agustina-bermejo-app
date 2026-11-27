package com.red_escuela.backend.graduation.debtexception.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendance;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceView;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendeeView;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationFinder;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationView;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificatePdf;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnApprovedSubjectsEnrollments;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnedEnrollment;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedSubjectsDebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.procedure.specialist.ExamDebtExceptionDocumentIssuer;
import com.red_escuela.backend.evaluation.api.OwnExamRegistrationFinder;
import com.red_escuela.backend.evaluation.api.OwnExamRegistrationView;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularEnrollmentNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentFacts;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentFactsFinder;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionAlreadyUsedException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocument;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionScope;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationCertificateIssuer;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationCertificatePdfProvider;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationDebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.procedure.specialist.RegularDebtExceptionDocumentIssuer;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DebtExceptionDocumentIssuersTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long OTHER_STUDENT_ID = 8L;
    private static final Long ENROLLMENT_ID = 21L;
    private static final Long REGISTRATION_ID = 31L;
    private static final Long BOARD_ID = 41L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private RegularCertificateIssuer regularCertificateIssuer;
    private RegularCertificatePdfProvider regularPdfProvider;
    private RegularEnrollmentFactsFinder factsFinder;
    private RegularDebtExceptionDocumentIssuer regularAdapter;
    private ExamRegistrationCertificateIssuer examCertificateIssuer;
    private ExamRegistrationCertificatePdfProvider examPdfProvider;
    private OwnExamRegistrationFinder registrationFinder;
    private ExamRegistrationDebtExceptionDocumentIssuer examAdapter;
    private ApprovedSubjectsCertificateIssuer approvedSubjectsIssuer;
    private ApprovedSubjectsCertificatePdfProvider approvedSubjectsPdfProvider;
    private OwnApprovedSubjectsEnrollments ownEnrollments;
    private ApprovedSubjectsDebtExceptionDocumentIssuer approvedSubjectsAdapter;
    private ExamCertificateIssuer examBoardIssuer;
    private ExamCertificatePdfProvider examBoardPdfProvider;
    private ExamBoardRegistrationFinder boardRegistrationFinder;
    private ExamBoardAttendanceReader boardReader;
    private ExamDebtExceptionDocumentIssuer examBoardAdapter;

    @BeforeEach
    void setUpAdapters() {
        regularCertificateIssuer = mock(RegularCertificateIssuer.class);
        regularPdfProvider = mock(RegularCertificatePdfProvider.class);
        factsFinder = mock(RegularEnrollmentFactsFinder.class);
        regularAdapter = new RegularDebtExceptionDocumentIssuer(regularCertificateIssuer, regularPdfProvider, factsFinder);
        examCertificateIssuer = mock(ExamRegistrationCertificateIssuer.class);
        examPdfProvider = mock(ExamRegistrationCertificatePdfProvider.class);
        registrationFinder = mock(OwnExamRegistrationFinder.class);
        examAdapter = new ExamRegistrationDebtExceptionDocumentIssuer(examCertificateIssuer, examPdfProvider, registrationFinder,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        approvedSubjectsIssuer = mock(ApprovedSubjectsCertificateIssuer.class);
        approvedSubjectsPdfProvider = mock(ApprovedSubjectsCertificatePdfProvider.class);
        ownEnrollments = mock(OwnApprovedSubjectsEnrollments.class);
        approvedSubjectsAdapter = new ApprovedSubjectsDebtExceptionDocumentIssuer(approvedSubjectsIssuer, approvedSubjectsPdfProvider,
                ownEnrollments);
        examBoardIssuer = mock(ExamCertificateIssuer.class);
        examBoardPdfProvider = mock(ExamCertificatePdfProvider.class);
        boardRegistrationFinder = mock(ExamBoardRegistrationFinder.class);
        boardReader = mock(ExamBoardAttendanceReader.class);
        examBoardAdapter = new ExamDebtExceptionDocumentIssuer(examBoardIssuer, examBoardPdfProvider, boardRegistrationFinder, boardReader);
    }

    @Test
    void concreteAdaptersAreRoutedByTheirOwnTypeOnly() {
        DebtExceptionDocumentIssuers issuers = new DebtExceptionDocumentIssuers(
                List.of(regularAdapter, examAdapter, approvedSubjectsAdapter, examBoardAdapter));

        assertThat(issuers.forType(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE)).containsSame(regularAdapter);
        assertThat(issuers.forType(CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE)).containsSame(examAdapter);
        assertThat(issuers.forType(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE)).containsSame(approvedSubjectsAdapter);
        assertThat(issuers.forType(CertificateDocumentType.EXAM_CERTIFICATE)).containsSame(examBoardAdapter);
        assertThat(issuers.forType(CertificateDocumentType.GRADUATION_CERTIFICATE)).isEmpty();
    }

    @Test
    void typeWithoutAdapterLeavesTheExceptionUnconsumed() {
        DocumentDebtExceptionRepository repository = mock(DocumentDebtExceptionRepository.class);
        DebtExceptionDownloader downloader = new DebtExceptionDownloader(repository, new DebtExceptionDocumentIssuers(List.of()),
                mock(DebtExceptionAuditRecorder.class), mock(CurrentActorFinder.class), Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        DocumentDebtException exception = mock(DocumentDebtException.class);
        when(exception.getDocumentType()).thenReturn(CertificateDocumentType.GRADUATION_CERTIFICATE);
        when(repository.findById(5L)).thenReturn(Optional.of(exception));

        assertThatThrownBy(() -> downloader.download(5L)).isInstanceOf(DocumentOperationPendingException.class);
        verify(repository, never()).markUsedIfActive(anyLong(), any(), anyLong());
    }

    @Test
    void approvedSubjectsAdapterRejectsAnEnrollmentOfAnotherStudent() {
        when(ownEnrollments.findOwnedBy(OTHER_STUDENT_ID, ENROLLMENT_ID)).thenReturn(Optional.empty());
        DebtExceptionScope foreign = approvedSubjectsScope(OTHER_STUDENT_ID, ENROLLMENT_ID);

        assertThatThrownBy(() -> approvedSubjectsAdapter.requireScopeBelongsToStudent(foreign))
                .isInstanceOf(DebtExceptionNotFoundException.class);
        assertThatThrownBy(() -> approvedSubjectsAdapter.render(foreign)).isInstanceOf(DebtExceptionNotFoundException.class);
        verify(approvedSubjectsIssuer, never()).issueUnderDebtException(anyLong(), anyLong());
    }

    @Test
    void approvedSubjectsAdapterRejectsAScopeWithoutEnrollment() {
        DebtExceptionScope withoutEnrollment = approvedSubjectsScope(STUDENT_ID, null);

        assertThatThrownBy(() -> approvedSubjectsAdapter.requireScopeBelongsToStudent(withoutEnrollment))
                .isInstanceOf(DebtExceptionNotFoundException.class);
    }

    @Test
    void approvedSubjectsAdapterIssuesForTheOwnEnrollmentWithAnAllowedAuditDetail() {
        when(ownEnrollments.findOwnedBy(STUDENT_ID, ENROLLMENT_ID)).thenReturn(Optional.of(new OwnedEnrollment(ENROLLMENT_ID, true)));
        ApprovedSubjectsCertificateResponse issued = mock(ApprovedSubjectsCertificateResponse.class);
        when(issued.id()).thenReturn(77L);
        when(approvedSubjectsIssuer.issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID)).thenReturn(issued);
        when(approvedSubjectsPdfProvider.pdfOfOwn(77L, STUDENT_ID)).thenReturn(new ApprovedSubjectsCertificatePdf("MAT456", new byte[]{3}));

        DebtExceptionDocument document = approvedSubjectsAdapter.render(approvedSubjectsScope(STUDENT_ID, ENROLLMENT_ID));

        assertThat(document.code()).isEqualTo("MAT456");
        assertThat(document.content()).containsExactly(3);
        assertThat(DebtExceptionAuditDetail.requireAllowed(document.auditDetail())).isEqualTo("documentId=77;code=MAT456");
    }

    @Test
    void examBoardAdapterRejectsARegistrationOfAnotherStudent() {
        when(boardRegistrationFinder.requireById(REGISTRATION_ID)).thenReturn(new ExamBoardRegistrationView(REGISTRATION_ID, BOARD_ID));
        when(boardReader.attendanceOf(BOARD_ID)).thenReturn(boardWith(new ExamBoardAttendeeView(REGISTRATION_ID, STUDENT_ID,
                ExamBoardAttendance.PRESENT)));
        DebtExceptionScope foreign = examBoardScope(OTHER_STUDENT_ID, REGISTRATION_ID);

        assertThatThrownBy(() -> examBoardAdapter.requireScopeBelongsToStudent(foreign)).isInstanceOf(DebtExceptionNotFoundException.class);
        assertThatThrownBy(() -> examBoardAdapter.render(foreign)).isInstanceOf(DebtExceptionNotFoundException.class);
        verify(examBoardIssuer, never()).issueUnderDebtException(anyLong(), anyLong());
    }

    @Test
    void examBoardAdapterRejectsAnUnknownRegistration() {
        when(boardRegistrationFinder.requireById(REGISTRATION_ID)).thenThrow(new ExamBoardRegistrationNotFoundException(REGISTRATION_ID));

        assertThatThrownBy(() -> examBoardAdapter.requireScopeBelongsToStudent(examBoardScope(STUDENT_ID, REGISTRATION_ID)))
                .isInstanceOf(DebtExceptionNotFoundException.class);
    }

    @Test
    void examBoardAdapterRejectsAScopeWithoutRegistration() {
        DebtExceptionScope withoutRegistration = examBoardScope(STUDENT_ID, null);

        assertThatThrownBy(() -> examBoardAdapter.requireScopeBelongsToStudent(withoutRegistration))
                .isInstanceOf(DebtExceptionNotFoundException.class);
    }

    @Test
    void examBoardAdapterIssuesForTheOwnRegistrationWithAnAllowedAuditDetail() {
        when(boardRegistrationFinder.requireById(REGISTRATION_ID)).thenReturn(new ExamBoardRegistrationView(REGISTRATION_ID, BOARD_ID));
        when(boardReader.attendanceOf(BOARD_ID)).thenReturn(boardWith(new ExamBoardAttendeeView(REGISTRATION_ID, STUDENT_ID,
                ExamBoardAttendance.PRESENT)));
        ExamCertificate issued = mock(ExamCertificate.class);
        when(issued.getId()).thenReturn(88L);
        when(examBoardIssuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID)).thenReturn(issued);
        when(examBoardPdfProvider.pdfOf(88L)).thenReturn(new GraduationCertificatePdf("EXA123", new byte[]{4}));

        DebtExceptionDocument document = examBoardAdapter.render(examBoardScope(STUDENT_ID, REGISTRATION_ID));

        assertThat(document.code()).isEqualTo("EXA123");
        assertThat(document.content()).containsExactly(4);
        assertThat(DebtExceptionAuditDetail.requireAllowed(document.auditDetail())).isEqualTo("documentId=88;code=EXA123");
    }

    @Test
    void regularAdapterRejectsAnEnrollmentOfAnotherStudent() {
        when(factsFinder.findOwnedBy(RegularityKind.PROGRAM, ENROLLMENT_ID, OTHER_STUDENT_ID))
                .thenThrow(new RegularEnrollmentNotFoundException());
        DebtExceptionScope foreign = regularScope(OTHER_STUDENT_ID, ENROLLMENT_ID);

        assertThatThrownBy(() -> regularAdapter.requireScopeBelongsToStudent(foreign)).isInstanceOf(DebtExceptionNotFoundException.class);
        assertThatThrownBy(() -> regularAdapter.render(foreign)).isInstanceOf(DebtExceptionNotFoundException.class);
        verify(regularCertificateIssuer, never()).issueUnderDebtException(anyLong(), anyLong());
    }

    @Test
    void regularAdapterRejectsAScopeWithoutEnrollment() {
        DebtExceptionScope withoutEnrollment = regularScope(STUDENT_ID, null);

        assertThatThrownBy(() -> regularAdapter.requireScopeBelongsToStudent(withoutEnrollment))
                .isInstanceOf(DebtExceptionNotFoundException.class);
    }

    @Test
    void regularAdapterIssuesForTheOwnEnrollmentWithAnAllowedAuditDetail() {
        when(factsFinder.findOwnedBy(RegularityKind.PROGRAM, ENROLLMENT_ID, STUDENT_ID)).thenReturn(mock(RegularEnrollmentFacts.class));
        RegularCertificateResponse issued = mock(RegularCertificateResponse.class);
        when(issued.id()).thenReturn(99L);
        when(regularCertificateIssuer.issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID)).thenReturn(issued);
        when(regularPdfProvider.pdfOf(99L)).thenReturn(new RegularCertificatePdf("ABC123", new byte[]{1}));

        DebtExceptionDocument document = regularAdapter.render(regularScope(STUDENT_ID, ENROLLMENT_ID));

        assertThat(document.code()).isEqualTo("ABC123");
        assertThat(document.content()).containsExactly(1);
        assertThat(DebtExceptionAuditDetail.requireAllowed(document.auditDetail())).isEqualTo("documentId=99;code=ABC123");
    }

    @Test
    void examAdapterRejectsARegistrationOfAnotherStudent() {
        when(registrationFinder.findActiveOwned(OTHER_STUDENT_ID, REGISTRATION_ID, NOW.toLocalDate().atStartOfDay()))
                .thenReturn(Optional.empty());
        DebtExceptionScope foreign = examScope(OTHER_STUDENT_ID, REGISTRATION_ID);

        assertThatThrownBy(() -> examAdapter.requireScopeBelongsToStudent(foreign)).isInstanceOf(DebtExceptionNotFoundException.class);
        assertThatThrownBy(() -> examAdapter.render(foreign)).isInstanceOf(DebtExceptionNotFoundException.class);
        verify(examCertificateIssuer, never()).issueUnderDebtException(anyLong(), anyLong());
    }

    @Test
    void examAdapterRejectsAScopeWithoutRegistration() {
        DebtExceptionScope withoutRegistration = examScope(STUDENT_ID, null);

        assertThatThrownBy(() -> examAdapter.requireScopeBelongsToStudent(withoutRegistration))
                .isInstanceOf(DebtExceptionNotFoundException.class);
    }

    @Test
    void examAdapterIssuesForTheOwnRegistrationWithAnAllowedAuditDetail() {
        when(registrationFinder.findActiveOwned(STUDENT_ID, REGISTRATION_ID, NOW.toLocalDate().atStartOfDay()))
                .thenReturn(Optional.of(mock(OwnExamRegistrationView.class)));
        ExamRegistrationCertificate issued = mock(ExamRegistrationCertificate.class);
        when(issued.getId()).thenReturn(55L);
        when(examCertificateIssuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID)).thenReturn(issued);
        when(examPdfProvider.pdfOfOwn(55L, STUDENT_ID)).thenReturn(new ProcedureDocumentPdf("XYZ789", new byte[]{2}));

        DebtExceptionDocument document = examAdapter.render(examScope(STUDENT_ID, REGISTRATION_ID));

        assertThat(document.code()).isEqualTo("XYZ789");
        assertThat(DebtExceptionAuditDetail.requireAllowed(document.auditDetail())).isEqualTo("documentId=55;code=XYZ789");
    }

    @Test
    void downloadThroughTheRegularAdapterConsumesTheExceptionExactlyOnce() {
        DocumentDebtExceptionRepository repository = mock(DocumentDebtExceptionRepository.class);
        CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
        when(actorFinder.currentPersonId()).thenReturn(9L);
        DebtExceptionDownloader downloader = new DebtExceptionDownloader(repository,
                new DebtExceptionDocumentIssuers(List.of(regularAdapter)), mock(DebtExceptionAuditRecorder.class), actorFinder,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        DocumentDebtException exception = mock(DocumentDebtException.class);
        when(exception.getId()).thenReturn(5L);
        when(exception.getStudentId()).thenReturn(STUDENT_ID);
        when(exception.getDocumentType()).thenReturn(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE);
        when(exception.getEnrollmentId()).thenReturn(ENROLLMENT_ID);
        when(exception.getStatus()).thenReturn(DebtExceptionStatus.ACTIVE);
        when(exception.getExpiresAt()).thenReturn(NOW.plusDays(1));
        when(repository.findById(5L)).thenReturn(Optional.of(exception));
        when(repository.markUsedIfActive(5L, NOW, 9L)).thenReturn(1);
        when(factsFinder.findOwnedBy(RegularityKind.PROGRAM, ENROLLMENT_ID, STUDENT_ID)).thenReturn(mock(RegularEnrollmentFacts.class));
        RegularCertificateResponse issued = mock(RegularCertificateResponse.class);
        when(issued.id()).thenReturn(99L);
        when(regularCertificateIssuer.issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID)).thenReturn(issued);
        when(regularPdfProvider.pdfOf(99L)).thenReturn(new RegularCertificatePdf("ABC123", new byte[]{1}));

        assertThat(downloader.download(5L).code()).isEqualTo("ABC123");
        verify(repository).markUsedIfActive(5L, NOW, 9L);

        when(exception.getStatus()).thenReturn(DebtExceptionStatus.USED);
        assertThatThrownBy(() -> downloader.download(5L)).isInstanceOf(DebtExceptionAlreadyUsedException.class);
        verify(regularCertificateIssuer).issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID);
    }

    @Test
    void downloadThroughTheApprovedSubjectsAdapterConsumesTheExceptionExactlyOnce() {
        DocumentDebtExceptionRepository repository = mock(DocumentDebtExceptionRepository.class);
        DebtExceptionDownloader downloader = downloaderWith(repository, approvedSubjectsAdapter);
        DocumentDebtException exception = activeException(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE, ENROLLMENT_ID, null);
        when(repository.findById(5L)).thenReturn(Optional.of(exception));
        when(repository.markUsedIfActive(5L, NOW, 9L)).thenReturn(1);
        when(ownEnrollments.findOwnedBy(STUDENT_ID, ENROLLMENT_ID)).thenReturn(Optional.of(new OwnedEnrollment(ENROLLMENT_ID, true)));
        ApprovedSubjectsCertificateResponse issued = mock(ApprovedSubjectsCertificateResponse.class);
        when(issued.id()).thenReturn(77L);
        when(approvedSubjectsIssuer.issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID)).thenReturn(issued);
        when(approvedSubjectsPdfProvider.pdfOfOwn(77L, STUDENT_ID)).thenReturn(new ApprovedSubjectsCertificatePdf("MAT456", new byte[]{3}));

        assertThat(downloader.download(5L).code()).isEqualTo("MAT456");
        verify(repository).markUsedIfActive(5L, NOW, 9L);

        when(exception.getStatus()).thenReturn(DebtExceptionStatus.USED);
        assertThatThrownBy(() -> downloader.download(5L)).isInstanceOf(DebtExceptionAlreadyUsedException.class);
        verify(approvedSubjectsIssuer).issueUnderDebtException(STUDENT_ID, ENROLLMENT_ID);
    }

    @Test
    void downloadThroughTheExamBoardAdapterConsumesTheExceptionExactlyOnce() {
        DocumentDebtExceptionRepository repository = mock(DocumentDebtExceptionRepository.class);
        DebtExceptionDownloader downloader = downloaderWith(repository, examBoardAdapter);
        DocumentDebtException exception = activeException(CertificateDocumentType.EXAM_CERTIFICATE, null, REGISTRATION_ID);
        when(repository.findById(5L)).thenReturn(Optional.of(exception));
        when(repository.markUsedIfActive(5L, NOW, 9L)).thenReturn(1);
        when(boardRegistrationFinder.requireById(REGISTRATION_ID)).thenReturn(new ExamBoardRegistrationView(REGISTRATION_ID, BOARD_ID));
        when(boardReader.attendanceOf(BOARD_ID)).thenReturn(boardWith(new ExamBoardAttendeeView(REGISTRATION_ID, STUDENT_ID,
                ExamBoardAttendance.PRESENT)));
        ExamCertificate issued = mock(ExamCertificate.class);
        when(issued.getId()).thenReturn(88L);
        when(examBoardIssuer.issueUnderDebtException(STUDENT_ID, REGISTRATION_ID)).thenReturn(issued);
        when(examBoardPdfProvider.pdfOf(88L)).thenReturn(new GraduationCertificatePdf("EXA123", new byte[]{4}));

        assertThat(downloader.download(5L).code()).isEqualTo("EXA123");
        verify(repository).markUsedIfActive(5L, NOW, 9L);

        when(exception.getStatus()).thenReturn(DebtExceptionStatus.USED);
        assertThatThrownBy(() -> downloader.download(5L)).isInstanceOf(DebtExceptionAlreadyUsedException.class);
        verify(examBoardIssuer).issueUnderDebtException(STUDENT_ID, REGISTRATION_ID);
    }

    private DebtExceptionDownloader downloaderWith(DocumentDebtExceptionRepository repository, DebtExceptionDocumentIssuer adapter) {
        CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
        when(actorFinder.currentPersonId()).thenReturn(9L);
        return new DebtExceptionDownloader(repository, new DebtExceptionDocumentIssuers(List.of(adapter)),
                mock(DebtExceptionAuditRecorder.class), actorFinder, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private static DocumentDebtException activeException(CertificateDocumentType type, Long enrollmentId, Long registrationId) {
        DocumentDebtException exception = mock(DocumentDebtException.class);
        when(exception.getId()).thenReturn(5L);
        when(exception.getStudentId()).thenReturn(STUDENT_ID);
        when(exception.getDocumentType()).thenReturn(type);
        when(exception.getEnrollmentId()).thenReturn(enrollmentId);
        when(exception.getRegistrationId()).thenReturn(registrationId);
        when(exception.getStatus()).thenReturn(DebtExceptionStatus.ACTIVE);
        when(exception.getExpiresAt()).thenReturn(NOW.plusDays(1));
        return exception;
    }

    private static ExamBoardAttendanceView boardWith(ExamBoardAttendeeView attendee) {
        return new ExamBoardAttendanceView(BOARD_ID, true, NOW.toLocalDate(), "Materia", List.of(attendee));
    }

    private static DebtExceptionScope approvedSubjectsScope(Long studentId, Long enrollmentId) {
        return new DebtExceptionScope(5L, studentId, CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE, enrollmentId, null);
    }

    private static DebtExceptionScope examBoardScope(Long studentId, Long registrationId) {
        return new DebtExceptionScope(5L, studentId, CertificateDocumentType.EXAM_CERTIFICATE, null, registrationId);
    }

    private static DebtExceptionScope regularScope(Long studentId, Long enrollmentId) {
        return new DebtExceptionScope(5L, studentId, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, enrollmentId, null);
    }

    private static DebtExceptionScope examScope(Long studentId, Long registrationId) {
        return new DebtExceptionScope(5L, studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, null, registrationId);
    }

    private record FakeIssuer(CertificateDocumentType type) implements DebtExceptionDocumentIssuer {

        @Override
        public boolean supports(CertificateDocumentType candidate) {
            return candidate == type;
        }

        @Override
        public void requireScopeBelongsToStudent(DebtExceptionScope scope) {
        }

        @Override
        public DebtExceptionDocument render(DebtExceptionScope scope) {
            return new DebtExceptionDocument("fake", new byte[0], null);
        }
    }
}
