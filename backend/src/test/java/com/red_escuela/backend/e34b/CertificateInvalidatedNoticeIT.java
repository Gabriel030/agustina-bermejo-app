package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.events.CertificateInvalidatedEvent;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@RecordApplicationEvents
class CertificateInvalidatedNoticeIT extends ReproductionFixture {

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private SectionEnrollmentOutcomeRecorder outcomeRecorder;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ApplicationEvents applicationEvents;

    private IssuedCertificateRows rows;

    @BeforeEach
    void createRows() {
        rows = new IssuedCertificateRows(entityManager);
    }

    private ProgramEnrollment enrolled() {
        return programEnrollment(student(), program(4), ProgramEnrollmentStatus.ENROLLED);
    }

    private List<CertificateInvalidatedEvent> invalidations() {
        return applicationEvents.stream(CertificateInvalidatedEvent.class).toList();
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
    void withdrawingTheProgramEnrollmentAnnouncesOneInvalidationPerRevokedCertificate() {
        ProgramEnrollment enrollment = enrolled();
        Long issuer = person().getId();
        Long regularId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), issuer);
        Long approvedId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), issuer);
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        flushAndClear();

        Long personId = enrollment.getStudent().getPerson().getId();
        assertThat(invalidations()).extracting(CertificateInvalidatedEvent::certificateId, CertificateInvalidatedEvent::certificateKind,
                CertificateInvalidatedEvent::personId).containsExactlyInAnyOrder(
                tuple(regularId, CertificateInvalidatedEvent.REGULAR, personId),
                tuple(approvedId, CertificateInvalidatedEvent.APPROVED_SUBJECTS, personId));
        assertThat(invalidations()).allSatisfy(event -> assertThat(event.reason()).isNotBlank());
    }

    @Test
    void losingApprovalTwiceAnnouncesTheInvalidationOnce() {
        ProgramEnrollment enrollment = enrolled();
        SubjectSectionEnrollment first = approvedSection(enrollment, "Algebra", "8.00");
        SubjectSectionEnrollment second = approvedSection(enrollment, "Fisica", "7.00");
        rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(first.getId(), EnrollmentStatus.REGULAR, null, null, true);
        outcomeRecorder.recordProgramOutcome(second.getId(), EnrollmentStatus.REGULAR, null, null, true);
        flushAndClear();

        assertThat(invalidations()).hasSize(1);
    }

    @Test
    void losingRegularityAnnouncesTheInvalidationOfTheRegularCertificate() {
        ProgramEnrollment enrollment = enrolled();
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment withdrawn = sectionEnrollment(enrollment, section(subject("Algebra"), term), EnrollmentStatus.WITHDRAWN);
        Long certificateId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), person().getId());
        flushAndClear();

        eventPublisher.publishEvent(new AttendanceStatusChangedEvent(withdrawn.getId(), null));
        flushAndClear();

        assertThat(invalidations()).singleElement().satisfies(event -> {
            assertThat(event.certificateId()).isEqualTo(certificateId);
            assertThat(event.certificateKind()).isEqualTo(CertificateInvalidatedEvent.REGULAR);
        });
    }

    @Test
    void certificatesThatWereNotValidAnnounceNothing() {
        ProgramEnrollment enrollment = enrolled();
        Long issuer = person().getId();
        Long revokedId = rows.validRegular(enrollment.getStudent().getId(), enrollment.getId(), issuer);
        Long expiredId = rows.validApprovedSubjects(enrollment.getStudent().getId(), enrollment.getId(), issuer);
        rows.markRegularAs(revokedId, "REVOKED");
        rows.markApprovedSubjectsAs(expiredId, "EXPIRED");
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        flushAndClear();

        assertThat(invalidations()).isEmpty();
    }

    @Test
    void withdrawingAnotherEnrollmentAnnouncesNothing() {
        ProgramEnrollment withdrawn = enrolled();
        ProgramEnrollment other = enrolled();
        rows.validRegular(other.getStudent().getId(), other.getId(), person().getId());
        flushAndClear();

        programEnrollmentService.withdrawForNonpayment(withdrawn.getId());
        flushAndClear();

        assertThat(invalidations()).isEmpty();
    }
}
