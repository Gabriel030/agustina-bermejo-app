package com.red_escuela.backend.e34b;

import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.EnrollmentChange;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateInvalidationIT extends ReproductionFixture {

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private SubjectSectionEnrollmentService subjectSectionEnrollmentService;
    @Autowired
    private SectionEnrollmentOutcomeRecorder outcomeRecorder;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private IssuedCertificateRows rows;

    @BeforeEach
    void createRows() {
        rows = new IssuedCertificateRows(entityManager);
    }

    private ProgramEnrollment enrolled() {
        return programEnrollment(student(), program(4), ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void withdrawingTheProgramEnrollmentRevokesTheValidRegularCertificate() {
        ProgramEnrollment enrollment = enrolled();
        Person issuer = person();
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), issuer.getId());
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        flushAndClear();

        assertThat(rows.regularStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void withdrawingTheProgramEnrollmentRevokesTheValidApprovedSubjectsCertificate() {
        ProgramEnrollment enrollment = enrolled();
        Person issuer = person();
        Long certificateId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), issuer.getId());
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("REVOKED");
        assertThat(rows.revocationReasonOfApprovedSubjects(certificateId)).isNotBlank();
    }

    @Test
    void withdrawingTheProgramEnrollmentClosesTheOpenGraduationRequest() {
        ProgramEnrollment enrollment = enrolled();
        Long requestId = rows.openGraduationRequest(enrollment.getId());
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        flushAndClear();

        assertThat(rows.graduationRequestStatus(requestId)).isEqualTo("REJECTED");
    }

    @Test
    void withdrawingAnotherProgramEnrollmentLeavesTheCertificatesValid() {
        ProgramEnrollment withdrawn = enrolled();
        ProgramEnrollment other = enrolled();
        Person issuer = person();
        Long regularId = rows.validRegular(other.getStudent().getId(), other.getId(), issuer.getId());
        Long approvedId = rows.validApprovedSubjects(other.getStudent().getId(), other.getId(), issuer.getId());
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(withdrawn.getId());
        flushAndClear();

        assertThat(rows.regularStatus(regularId)).isEqualTo("VALID");
        assertThat(rows.approvedSubjectsStatus(approvedId)).isEqualTo("VALID");
    }

    @Test
    void losingTheActiveSectionRevokesTheRegularCertificateWhenAttendanceChanges() {
        ProgramEnrollment enrollment = enrolled();
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment withdrawnSection = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.WITHDRAWN);
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        eventPublisher.publishEvent(new AttendanceStatusChangedEvent(withdrawnSection.getId(), null));
        flushAndClear();

        assertThat(rows.regularStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void attendanceChangeKeepsTheRegularCertificateWhenTheStudentIsStillRegular() {
        ProgramEnrollment enrollment = enrolled();
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment active = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.IN_PROGRESS);
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        eventPublisher.publishEvent(new AttendanceStatusChangedEvent(active.getId(), null));
        flushAndClear();

        assertThat(rows.regularStatus(certificateId)).isEqualTo("VALID");
    }

    private SubjectSectionEnrollment approvedSection(ProgramEnrollment enrollment, String subjectName, String score) {
        Subject subject = subject(subjectName);
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment section = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.APPROVED);
        section.setFinalScore(Grade.of(new BigDecimal(score)));
        section.setFinalGradeType(FinalGradeType.FINAL_EXAM);
        return section;
    }

    @Test
    void losingTheApprovalOfASectionRevokesTheApprovedSubjectsCertificate() {
        ProgramEnrollment enrollment = enrolled();
        SubjectSectionEnrollment section = approvedSection(enrollment, "Algebra", "8.00");
        Long certificateId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(section.getId(), EnrollmentStatus.REGULAR, null, null, true);
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void changingTheFinalScoreOfAnApprovedSectionRevokesTheApprovedSubjectsCertificate() {
        ProgramEnrollment enrollment = enrolled();
        SubjectSectionEnrollment section = approvedSection(enrollment, "Algebra", "8.00");
        Long certificateId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(section.getId(), EnrollmentStatus.APPROVED, Grade.of(new BigDecimal("9.00")),
                FinalGradeType.FINAL_EXAM, true);
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void recordingTheSameApprovedOutcomeKeepsTheApprovedSubjectsCertificateValid() {
        ProgramEnrollment enrollment = enrolled();
        SubjectSectionEnrollment section = approvedSection(enrollment, "Algebra", "8.00");
        Long certificateId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(section.getId(), EnrollmentStatus.APPROVED, Grade.of(new BigDecimal("8.00")),
                FinalGradeType.FINAL_EXAM, true);
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("VALID");
    }

    @Test
    void gainingApprovalKeepsTheApprovedSubjectsCertificateValid() {
        ProgramEnrollment enrollment = enrolled();
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment section = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.REGULAR);
        Long certificateId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(section.getId(), EnrollmentStatus.APPROVED, Grade.of(new BigDecimal("8.00")),
                FinalGradeType.FINAL_EXAM, true);
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("VALID");
    }

    @Test
    void losingApprovalTwiceRevokesOnceAndKeepsTheFirstReason() {
        ProgramEnrollment enrollment = enrolled();
        SubjectSectionEnrollment first = approvedSection(enrollment, "Algebra", "8.00");
        SubjectSectionEnrollment second = approvedSection(enrollment, "Fisica", "7.00");
        Long certificateId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(first.getId(), EnrollmentStatus.REGULAR, null, null, true);
        flushAndClear();
        String reason = rows.revocationReasonOfApprovedSubjects(certificateId);
        outcomeRecorder.recordProgramOutcome(second.getId(), EnrollmentStatus.REGULAR, null, null, true);
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("REVOKED");
        assertThat(rows.revocationReasonOfApprovedSubjects(certificateId)).isEqualTo(reason);
        assertThat(rows.auditEntries("APPROVED_SUBJECTS_CERT_REVOKED", certificateId)).isEqualTo(1);
    }

    @Test
    void expiredApprovedSubjectsCertificateIsNotRevokedWhenApprovalIsLost() {
        ProgramEnrollment enrollment = enrolled();
        SubjectSectionEnrollment section = approvedSection(enrollment, "Algebra", "8.00");
        Long certificateId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        rows.markApprovedSubjectsAs(certificateId, "EXPIRED");
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(section.getId(), EnrollmentStatus.REGULAR, null, null, true);
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("EXPIRED");
    }

    @Test
    void withdrawingTheProgramEnrollmentKeepsAlreadyRevokedCertificatesUntouched() {
        ProgramEnrollment enrollment = enrolled();
        Long approvedId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        Long regularId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        rows.markApprovedSubjectsAs(approvedId, "REVOKED");
        rows.markRegularAs(regularId, "REVOKED");
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        flushAndClear();

        assertThat(rows.approvedSubjectsStatus(approvedId)).isEqualTo("REVOKED");
        assertThat(rows.regularStatus(regularId)).isEqualTo("REVOKED");
        assertThat(rows.auditEntries("APPROVED_SUBJECTS_CERT_REVOKED", approvedId)).isZero();
        assertThat(rows.auditEntries("REGULAR_CERTIFICATE_REVOKED", regularId)).isZero();
    }

    @Test
    void endingTheOnlyActiveSectionRevokesTheRegularCertificate() {
        ProgramEnrollment enrollment = enrolled();
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment active = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.IN_PROGRESS);
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(active.getId(), EnrollmentStatus.FAILED, null, null, true);
        flushAndClear();

        assertThat(rows.regularStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void withdrawingTheOnlyActiveSectionRevokesTheRegularCertificate() {
        ProgramEnrollment enrollment = enrolled();
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment active = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.IN_PROGRESS);
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        subjectSectionEnrollmentService.withdraw(active.getId(), EnrollmentChange.byStaff("Baja administrativa del alumno", person().getId()));
        flushAndClear();

        assertThat(rows.regularStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void endingASectionKeepsTheRegularCertificateWhenAnotherSectionIsStillActive() {
        ProgramEnrollment enrollment = enrolled();
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment ended = sectionEnrollment(enrollment, section(subject("Algebra"), term), EnrollmentStatus.IN_PROGRESS);
        sectionEnrollment(enrollment, section(subject("Fisica"), term), EnrollmentStatus.IN_PROGRESS);
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(ended.getId(), EnrollmentStatus.FAILED, null, null, true);
        flushAndClear();

        assertThat(rows.regularStatus(certificateId)).isEqualTo("VALID");
    }

    @Test
    void automaticRegularRevocationIsAuditedWithoutAnActor() {
        ProgramEnrollment enrollment = enrolled();
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        flushAndClear();

        assertThat(rows.auditEntries("REGULAR_CERTIFICATE_REVOKED", certificateId)).isEqualTo(1);
        assertThat(rows.auditActor("REGULAR_CERTIFICATE_REVOKED", certificateId)).isNull();
        assertThat(rows.revocationReasonOfRegular(certificateId)).isNotBlank();
    }
}
