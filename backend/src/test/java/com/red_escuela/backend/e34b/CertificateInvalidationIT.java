package com.red_escuela.backend.e34b;

import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateInvalidationIT extends ReproductionFixture {

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
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
}
