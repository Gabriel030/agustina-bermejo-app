package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CourseworkConditionRecalculatorAtomicityIT extends AbstractIntegrationTest {

    private static final AtomicInteger YEARS = new AtomicInteger(2300);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private boolean sabotaged;

    @AfterEach
    void removeRecalculationSabotage() {
        if (sabotaged) {
            jdbcTemplate.execute("drop trigger if exists fail_condition_recalculation on subject_section_enrollment");
            jdbcTemplate.execute("drop function if exists fail_condition_recalculation()");
            sabotaged = false;
        }
    }

    @Test
    void attendanceChangeWhoseConditionRecalculationFailsLeavesTheAttendanceUnrecorded() {
        Long enrollmentId = seedFailedEnrollment();
        Long instructorId = seedInstructor();
        failEveryEnrollmentUpdate();
        long attendancesBefore = attendanceCount();

        assertThatThrownBy(() -> inOneTransaction(() -> {
            Attendance attendance = recordAbsence(enrollmentId, instructorId);
            eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(attendance));
        })).hasMessageContaining("condition recalculation failed");

        assertThat(attendanceCount()).isEqualTo(attendancesBefore);
    }

    @Test
    void attendanceChangeWhoseConditionRecalculationSucceedsCommitsTheAttendance() {
        Long enrollmentId = seedFailedEnrollment();
        Long instructorId = seedInstructor();
        long attendancesBefore = attendanceCount();

        inOneTransaction(() -> {
            Attendance attendance = recordAbsence(enrollmentId, instructorId);
            eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(attendance));
        });

        assertThat(attendanceCount()).isEqualTo(attendancesBefore + 1);
    }

    private void failEveryEnrollmentUpdate() {
        jdbcTemplate.execute("""
                create or replace function fail_condition_recalculation() returns trigger as $$
                begin
                    raise exception 'condition recalculation failed';
                end
                $$ language plpgsql
                """);
        jdbcTemplate.execute("drop trigger if exists fail_condition_recalculation on subject_section_enrollment");
        jdbcTemplate.execute("create trigger fail_condition_recalculation before update on subject_section_enrollment"
                + " for each row execute function fail_condition_recalculation()");
        sabotaged = true;
    }

    private long attendanceCount() {
        return jdbcTemplate.queryForObject("select count(*) from attendance", Long.class);
    }

    private void inOneTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private Attendance recordAbsence(Long enrollmentId, Long instructorId) {
        Attendance attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(entityManager.getReference(SubjectSectionEnrollment.class, enrollmentId));
        attendance.setClassDate(LocalDate.of(2025, 9, 15));
        attendance.setStatus(AttendanceStatus.ABSENT);
        attendance.setRecordedBy(entityManager.getReference(Instructor.class, instructorId));
        entityManager.persist(attendance);
        entityManager.flush();
        return attendance;
    }

    private Long seedInstructor() {
        Long[] id = new Long[1];
        inOneTransaction(() -> {
            Instructor instructor = new CycleTestFixtures(entityManager).instructor();
            entityManager.flush();
            id[0] = instructor.getId();
        });
        return id[0];
    }

    private Long seedFailedEnrollment() {
        Long[] id = new Long[1];
        inOneTransaction(() -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Term term = fixtures.term(YEARS.incrementAndGet());
            Program program = fixtures.program();
            var cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            section.setEvaluationSchemeId(scheme().getId());

            Institution institution = new Institution();
            institution.setName("Test institute");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(fixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(section);
            enrollment.setStatus(EnrollmentStatus.FAILED);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            entityManager.flush();
            id[0] = enrollment.getId();
        });
        return id[0];
    }

    private EvaluationScheme scheme() {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Scheme " + YEARS.get());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6)));
        scheme.setPromotionMinGrade(Grade.of(BigDecimal.valueOf(7)));
        entityManager.persist(scheme);
        return scheme;
    }
}
