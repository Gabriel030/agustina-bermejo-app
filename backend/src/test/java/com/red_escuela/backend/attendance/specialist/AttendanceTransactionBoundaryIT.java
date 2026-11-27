package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestRejectedEvent;
import com.red_escuela.backend.attendance.events.AttendanceJustificationApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.usecase.changerequest.ApproveAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.RejectAttendanceChangeRequestCommand;
import com.red_escuela.backend.attendance.usecase.changerequest.RejectAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.record.ApproveAttendanceJustificationUseCase;
import com.red_escuela.backend.attendance.usecase.record.JustifyAttendanceCommand;
import com.red_escuela.backend.attendance.usecase.record.JustifyAttendanceUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.red_escuela.backend.testsupport.FailingEventListenersConfiguration;
import com.red_escuela.backend.testsupport.FailingEventListenersConfiguration.FailingAttendanceListener;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(FailingEventListenersConfiguration.class)
class AttendanceTransactionBoundaryIT extends AbstractIntegrationTest {

    private static final LocalDate CLASS_DAY = LocalDate.of(2025, 9, 15);

    @Autowired
    private ApproveAttendanceChangeRequestUseCase approveChangeRequest;

    @Autowired
    private RejectAttendanceChangeRequestUseCase rejectChangeRequest;

    @Autowired
    private ApproveAttendanceJustificationUseCase approveJustification;

    @Autowired
    private JustifyAttendanceUseCase justifyAttendance;

    @Autowired
    private FailingAttendanceListener failingListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Instructor instructor;
    private SubjectSectionEnrollment enrollment;
    private int nextDayOffset;

    @BeforeEach
    void seedAnEnrolledStudentAndAnActingStaffMember() {
        MutableClock.INSTANCE.setDate(CLASS_DAY.plusDays(1));
        nextDayOffset = 0;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Term term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
            var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            instructor = fixtures.instructor();

            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(fixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(fixtures.program());
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);
            enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(section);
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);

            Person staff = fixtures.person();
            entityManager.flush();
            entityManager.createNativeQuery("insert into preceptor_assignment (person_id, subject_section_id, created_by) values (?1, ?2, ?1)")
                    .setParameter(1, staff.getId())
                    .setParameter(2, section.getId())
                    .executeUpdate();
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(staff.getId().toString(), null));
        });
    }

    @AfterEach
    void cleanUp() {
        failingListener.stopFailing();
        SecurityContextHolder.clearContext();
    }

    private Long persistAttendance(AttendanceStatus status) {
        Long[] id = new Long[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(entityManager.getReference(SubjectSectionEnrollment.class, enrollment.getId()));
            attendance.setClassDate(CLASS_DAY.minusDays(nextDayOffset++));
            attendance.setStatus(status);
            attendance.setRecordedBy(entityManager.getReference(Instructor.class, instructor.getId()));
            if (status == AttendanceStatus.JUSTIFICATION_PENDING) {
                attendance.setJustificationReason("medical visit");
                attendance.setJustificationAttachmentUrl("students/1/proof.pdf");
            }
            entityManager.persist(attendance);
            entityManager.flush();
            id[0] = attendance.getId();
        });
        return id[0];
    }

    private Long persistPendingChangeRequest(Long attendanceId) {
        Long[] id = new Long[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            AttendanceChangeRequest request = new AttendanceChangeRequest();
            request.setAttendance(entityManager.getReference(Attendance.class, attendanceId));
            request.setRequestedBy(entityManager.getReference(Instructor.class, instructor.getId()));
            request.setPreviousStatus(AttendanceStatus.ABSENT);
            request.setRequestedStatus(AttendanceStatus.PRESENT);
            request.setReason("student arrived late and was marked absent");
            request.setStatus(AttendanceChangeRequestStatus.PENDING);
            entityManager.persist(request);
            entityManager.flush();
            id[0] = request.getId();
        });
        return id[0];
    }

    private String attendanceStatus(Long attendanceId) {
        return jdbcTemplate.queryForObject("select status from attendance where id = ?", String.class, attendanceId);
    }

    private String requestStatus(Long requestId) {
        return jdbcTemplate.queryForObject("select status from attendance_change_request where id = ?", String.class, requestId);
    }

    @Test
    void approvingAChangeRequestFailingOnTheLastEventLeavesBothTheRequestAndTheAttendanceUntouched() {
        Long attendanceId = persistAttendance(AttendanceStatus.ABSENT);
        Long requestId = persistPendingChangeRequest(attendanceId);
        failingListener.failOn(AttendanceChangeRequestApprovedEvent.class);

        assertThatThrownBy(() -> approveChangeRequest.approve(requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
        assertThat(attendanceStatus(attendanceId)).isEqualTo("ABSENT");
    }

    @Test
    void approvingAChangeRequestAppliesTheRequestedStatusAndMarksTheRequestApproved() {
        Long attendanceId = persistAttendance(AttendanceStatus.ABSENT);
        Long requestId = persistPendingChangeRequest(attendanceId);

        approveChangeRequest.approve(requestId);

        assertThat(requestStatus(requestId)).isEqualTo("APPROVED");
        assertThat(attendanceStatus(attendanceId)).isEqualTo("PRESENT");
    }

    @Test
    void rejectingAChangeRequestFailingOnItsEventLeavesTheRequestPending() {
        Long attendanceId = persistAttendance(AttendanceStatus.ABSENT);
        Long requestId = persistPendingChangeRequest(attendanceId);
        failingListener.failOn(AttendanceChangeRequestRejectedEvent.class);

        assertThatThrownBy(() -> rejectChangeRequest.reject(new RejectAttendanceChangeRequestCommand(requestId, "no evidence")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("select rejection_reason from attendance_change_request where id = ?",
                String.class, requestId)).isNull();
    }

    @Test
    void rejectingAChangeRequestMarksItRejectedAndKeepsTheAttendance() {
        Long attendanceId = persistAttendance(AttendanceStatus.ABSENT);
        Long requestId = persistPendingChangeRequest(attendanceId);

        rejectChangeRequest.reject(new RejectAttendanceChangeRequestCommand(requestId, "no evidence"));

        assertThat(requestStatus(requestId)).isEqualTo("REJECTED");
        assertThat(attendanceStatus(attendanceId)).isEqualTo("ABSENT");
    }

    @Test
    void approvingAJustificationFailingOnTheLastEventKeepsItPending() {
        Long attendanceId = persistAttendance(AttendanceStatus.JUSTIFICATION_PENDING);
        failingListener.failOn(AttendanceJustificationApprovedEvent.class);

        assertThatThrownBy(() -> approveJustification.approve(attendanceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(attendanceStatus(attendanceId)).isEqualTo("JUSTIFICATION_PENDING");
    }

    @Test
    void approvingAJustificationLeavesTheAttendanceJustified() {
        Long attendanceId = persistAttendance(AttendanceStatus.JUSTIFICATION_PENDING);

        approveJustification.approve(attendanceId);

        assertThat(attendanceStatus(attendanceId)).isEqualTo("JUSTIFIED");
    }

    @Test
    void justifyingFailingOnTheStatusChangedEventKeepsTheAbsenceUnjustified() {
        Long attendanceId = persistAttendance(AttendanceStatus.ABSENT);
        failingListener.failOn(AttendanceStatusChangedEvent.class);

        assertThatThrownBy(() -> justifyAttendance.justify(new JustifyAttendanceCommand(attendanceId, "medical certificate", null, "late")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(attendanceStatus(attendanceId)).isEqualTo("ABSENT");
        assertThat(jdbcTemplate.queryForObject("select justification_reason from attendance where id = ?",
                String.class, attendanceId)).isNull();
    }

    @Test
    void justifyingAnAbsenceLeavesItJustifiedWithTheReason() {
        Long attendanceId = persistAttendance(AttendanceStatus.ABSENT);

        justifyAttendance.justify(new JustifyAttendanceCommand(attendanceId, "medical certificate", null, "late"));

        assertThat(attendanceStatus(attendanceId)).isEqualTo("JUSTIFIED");
        assertThat(jdbcTemplate.queryForObject("select justification_reason from attendance where id = ?",
                String.class, attendanceId)).startsWith("medical certificate");
    }
}
