package com.red_escuela.backend.graduation.certificate.exam.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendance;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceView;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendeeView;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchOutcome;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.IssueExamCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateBoardNotHeldException;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExamCertificateBatchIssuerTest {

    private static final Long BOARD = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private final ExamBoardAttendanceReader boardReader = mock(ExamBoardAttendanceReader.class);
    private final ExamCertificateRepository repository = mock(ExamCertificateRepository.class);
    private final ExamCertificatePaymentsGate paymentsGate = mock(ExamCertificatePaymentsGate.class);
    private final ExamCertificateIssuer issuer = mock(ExamCertificateIssuer.class);
    private final StudentFinder studentFinder = mock(StudentFinder.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final ExamCertificateBatchIssuer batchIssuer = new ExamCertificateBatchIssuer(boardReader, repository, paymentsGate, issuer,
            studentFinder, auditTrail, clock);

    @BeforeEach
    void stubCommon() {
        Student student = mock(Student.class);
        Person person = mock(Person.class);
        when(student.getPerson()).thenReturn(person);
        when(person.getLastname()).thenReturn("Perez");
        when(person.getName()).thenReturn("Ana");
        when(studentFinder.requireById(any())).thenReturn(student);
        when(paymentsGate.studentsWithDebt(any())).thenReturn(Set.of());
        when(repository.findByExamBoardIdAndRegistrationIdIn(any(), anyList())).thenReturn(List.of());
        when(issuer.issue(any())).thenAnswer(invocation -> certificate(500L, DocumentValidityStatus.VALID, NOW.plusDays(30), 0L));
    }

    private void givenBoard(boolean held, ExamBoardAttendeeView... attendees) {
        when(boardReader.attendanceOf(BOARD)).thenReturn(new ExamBoardAttendanceView(BOARD, held, LocalDate.of(2026, 11, 2),
                "Anatomía", List.of(attendees)));
    }

    private static ExamBoardAttendeeView attendee(long registrationId, long studentId, ExamBoardAttendance attendance) {
        return new ExamBoardAttendeeView(registrationId, studentId, attendance);
    }

    private static ExamCertificate certificate(Long id, DocumentValidityStatus status, LocalDateTime expiresAt, Long registrationId) {
        return ExamCertificate.builder().id(id).registrationId(registrationId).status(status).expiresAt(expiresAt).build();
    }

    private ExamCertificateBatchResponse run(Long... registrationIds) {
        return batchIssuer.issueBatch(BOARD, new IssueExamCertificateBatchRequest(List.of(registrationIds), null, null));
    }

    private ExamCertificateBatchOutcome outcomeOf(ExamCertificateBatchResponse response, long registrationId) {
        return response.items().stream().filter(item -> item.registrationId() == registrationId).findFirst().orElseThrow().outcome();
    }

    @Test
    void boardNotClosedIsRejectedWithoutIssuing() {
        givenBoard(false, attendee(1, 100, ExamBoardAttendance.PRESENT));

        assertThatThrownBy(() -> run(1L)).isInstanceOf(ExamCertificateBoardNotHeldException.class);
        verify(issuer, never()).issue(any());
    }

    @Test
    void presentStudentWithoutDebtIsIssued() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));

        ExamCertificateBatchResponse response = run(1L);

        assertThat(response.issuedCount()).isEqualTo(1);
        assertThat(outcomeOf(response, 1)).isEqualTo(ExamCertificateBatchOutcome.ISSUED);
    }

    @Test
    void debtorIsSkippedWithDebtOutcomeAndOthersStillIssued() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT), attendee(2, 200, ExamBoardAttendance.PRESENT));
        when(paymentsGate.studentsWithDebt(any())).thenReturn(Set.of(200L));

        ExamCertificateBatchResponse response = run(1L, 2L);

        assertThat(outcomeOf(response, 1)).isEqualTo(ExamCertificateBatchOutcome.ISSUED);
        assertThat(outcomeOf(response, 2)).isEqualTo(ExamCertificateBatchOutcome.DEBT);
        assertThat(response.issuedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
    }

    @Test
    void absentStudentIsNeverCheckedForDebt() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.ABSENT));

        ExamCertificateBatchResponse response = run(1L);

        assertThat(outcomeOf(response, 1)).isEqualTo(ExamCertificateBatchOutcome.ABSENT);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(paymentsGate).studentsWithDebt(captor.capture());
        assertThat(captor.getValue()).isEmpty();
        verify(issuer, never()).issue(any());
    }

    @Test
    void foreignRegistrationIdIsNotRegisteredAndNeverIssued() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));

        ExamCertificateBatchResponse response = run(999L);

        assertThat(outcomeOf(response, 999)).isEqualTo(ExamCertificateBatchOutcome.NOT_REGISTERED);
        assertThat(response.items().get(0).studentName()).isNull();
        verify(issuer, never()).issue(any());
    }

    @Test
    void notAttendingRegistrationIsNotRegistered() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.NOT_ATTENDING));

        assertThat(outcomeOf(run(1L), 1)).isEqualTo(ExamCertificateBatchOutcome.NOT_REGISTERED);
    }

    @Test
    void validCertificateIsAlreadyIssuedEvenIfStudentNowOwes() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));
        when(repository.findByExamBoardIdAndRegistrationIdIn(any(), anyList()))
                .thenReturn(List.of(certificate(7L, DocumentValidityStatus.VALID, NOW.plusDays(5), 1L)));
        when(paymentsGate.studentsWithDebt(any())).thenReturn(Set.of(100L));

        ExamCertificateBatchResponse response = run(1L);

        assertThat(outcomeOf(response, 1)).isEqualTo(ExamCertificateBatchOutcome.ALREADY_ISSUED);
        assertThat(response.items().get(0).certificateId()).isEqualTo(7L);
        verify(issuer, never()).issue(any());
    }

    @Test
    void revokedCertificateIsReissuedInBatch() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));
        when(repository.findByExamBoardIdAndRegistrationIdIn(any(), anyList()))
                .thenReturn(List.of(certificate(7L, DocumentValidityStatus.REVOKED, NOW.plusDays(5), 1L)));

        assertThat(outcomeOf(run(1L), 1)).isEqualTo(ExamCertificateBatchOutcome.ISSUED);
    }

    @Test
    void expiredValidCertificateIsExpiredAndReissued() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));
        ExamCertificate old = certificate(7L, DocumentValidityStatus.VALID, NOW.minusDays(1), 1L);
        when(repository.findByExamBoardIdAndRegistrationIdIn(any(), anyList())).thenReturn(List.of(old));

        assertThat(outcomeOf(run(1L), 1)).isEqualTo(ExamCertificateBatchOutcome.ISSUED);
        assertThat(old.getStatus()).isEqualTo(DocumentValidityStatus.EXPIRED);
    }

    @Test
    void expiredCertificateOfDebtorIsNotReissued() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));
        ExamCertificate old = certificate(7L, DocumentValidityStatus.VALID, NOW.minusDays(1), 1L);
        when(repository.findByExamBoardIdAndRegistrationIdIn(any(), anyList())).thenReturn(List.of(old));
        when(paymentsGate.studentsWithDebt(any())).thenReturn(Set.of(100L));

        assertThat(outcomeOf(run(1L), 1)).isEqualTo(ExamCertificateBatchOutcome.DEBT);
        assertThat(old.getStatus()).isEqualTo(DocumentValidityStatus.VALID);
        verify(issuer, never()).issue(any());
    }

    @Test
    void duplicatedRegistrationIdsAreIssuedOnce() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));

        ExamCertificateBatchResponse response = run(1L, 1L);

        assertThat(response.items()).hasSize(1);
        verify(issuer).issue(any());
    }

    @Test
    void staffActorMarksOriginAsIssuedByStaffAndTeacherAsIssuedByTeacher() {
        givenBoard(true, attendee(1, 100, ExamBoardAttendance.PRESENT));
        ArgumentCaptor<ExamCertificateIssuer.ExamCertificateIssueCommand> captor =
                ArgumentCaptor.forClass(ExamCertificateIssuer.ExamCertificateIssueCommand.class);

        when(boardReader.actorIsStaff()).thenReturn(true);
        run(1L);
        when(boardReader.actorIsStaff()).thenReturn(false);
        run(1L);

        verify(issuer, org.mockito.Mockito.times(2)).issue(captor.capture());
        assertThat(captor.getAllValues()).extracting(ExamCertificateIssuer.ExamCertificateIssueCommand::origin)
                .containsExactly(DocumentOrigin.ISSUED_BY_STAFF, DocumentOrigin.ISSUED_BY_TEACHER);
    }
}
