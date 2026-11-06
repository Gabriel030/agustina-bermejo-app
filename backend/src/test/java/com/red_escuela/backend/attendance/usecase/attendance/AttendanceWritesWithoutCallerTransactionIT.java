package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.RequestAttendanceChangeCommand;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.RequestAttendanceChangeUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.red_escuela.backend.shared.time.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AttendanceWritesWithoutCallerTransactionIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private LoadProgramSectionAttendanceUseCase loadAttendance;
    @Autowired
    private SubmitAttendanceProofUseCase submitProof;
    @Autowired
    private RequestAttendanceChangeUseCase requestChange;

    private Long attendanceId;
    private Long ownerId;
    private Long strangerId;

    @BeforeEach
    void seedAbsenceInAnOwnedSection() {
        MutableClock.INSTANCE.setDate(LocalDate.of(2025, 9, 16));
        transactionTemplate.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Term term = fixtures.term(2100 + (int) (System.nanoTime() % 800));
            var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            Instructor owner = fixtures.instructor();
            Instructor stranger = fixtures.instructor();
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(owner);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);
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
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(section);
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            entityManager.flush();
            attendanceId = loadAttendance.load(new LoadProgramSectionAttendanceCommand(section.getId(), owner.getId(),
                    new CargarAsistenciaRequest(LocalDate.of(2025, 9, 15), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null))).get(0).getId();
            ownerId = owner.getId();
            strangerId = stranger.getId();
        });
    }

    @Test
    void submitProofPersistsProofRowWithoutCallerTransaction() {
        submitProof.submit(new SubmitAttendanceProofCommand(attendanceId, "medical visit", "students/1/proof.pdf"));

        assertThat(jdbcTemplate.queryForObject("select justification_reason from attendance where id = ?", String.class, attendanceId)).isEqualTo("medical visit");
        assertThat(jdbcTemplate.queryForObject("select justification_attachment_url from attendance where id = ?", String.class, attendanceId)).isEqualTo("students/1/proof.pdf");
    }

    @Test
    void requestChangePersistsPendingRequestWithoutCallerTransaction() {
        requestChange.request(new RequestAttendanceChangeCommand(attendanceId, ownerId,
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "student arrived late and was marked absent")));

        assertThat(jdbcTemplate.queryForObject("select count(*) from attendance_change_request where attendance_id = ? and requested_by_id = ?", Long.class, attendanceId, ownerId))
                .isEqualTo(1L);
    }

    @Test
    void requestChangeByNonOwnerLeavesNoRequestRow() {
        assertThatThrownBy(() -> requestChange.request(new RequestAttendanceChangeCommand(attendanceId, strangerId,
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "student arrived late and was marked absent"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from attendance_change_request where attendance_id = ?", Long.class, attendanceId)).isZero();
    }
}
