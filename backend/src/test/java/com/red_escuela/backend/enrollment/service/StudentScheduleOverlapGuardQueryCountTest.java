package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionScheduleOverlapException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentScheduleOverlapGuardQueryCountTest extends AbstractIntegrationTest {

    private static final int FEW = 5;
    private static final int MANY = 50;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private StudentScheduleOverlapGuard guard;

    private CycleTestFixtures fixtures;
    private Term term;
    private Institution institution;
    private Student student;
    private ProgramEnrollment programEnrollment;

    @BeforeEach
    void setUp() {
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(2026);
        Institution institution = new Institution();
        institution.setName("Overlap institute");
        entityManager.persist(institution);
        this.institution = institution;
        newStudent();
    }

    private void newStudent() {
        student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        Program program = fixtures.program();
        programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
    }

    private SubjectSection section(DayOfWeek day, int fromHour, int toHour) {
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(section, fixtures.classroom(30), day, LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
        return section;
    }

    private SubjectSectionEnrollment enroll(SubjectSection section, EnrollmentStatus status) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Optional<SectionScheduleOverlapException> check(SubjectSection target) {
        fixtures.flushAndClear();
        return guard.findOverlapForProgramSection(student.getId(), target.getId(), term.getId(), null);
    }

    @Test
    void noOverlapWhenAllActiveEnrollmentsAreOnOtherDaysOrAdjacentHours() {
        SubjectSection target = section(DayOfWeek.MONDAY, 10, 12);
        enroll(section(DayOfWeek.MONDAY, 8, 10), EnrollmentStatus.ENROLLED);
        enroll(section(DayOfWeek.TUESDAY, 10, 12), EnrollmentStatus.IN_PROGRESS);

        assertThat(check(target)).isEmpty();
    }

    @Test
    void partialOverlapNamesTheConflictingSubjectDayAndTimes() {
        SubjectSection target = section(DayOfWeek.MONDAY, 9, 11);
        SubjectSection other = section(DayOfWeek.MONDAY, 8, 10);
        enroll(other, EnrollmentStatus.ENROLLED);

        Optional<SectionScheduleOverlapException> overlap = check(target);

        assertThat(overlap).isPresent();
        assertThat(overlap.get().getMessage()).contains(other.getSubject().getName()).contains("lunes").contains("08:00").contains("10:00");
    }

    @Test
    void sameSectionIsNeverAConflictWithItself() {
        SubjectSection target = section(DayOfWeek.MONDAY, 9, 11);
        enroll(target, EnrollmentStatus.ENROLLED);

        assertThat(check(target)).isEmpty();
    }

    @Test
    void inactiveEnrollmentsDoNotConflict() {
        SubjectSection target = section(DayOfWeek.MONDAY, 9, 11);
        enroll(section(DayOfWeek.MONDAY, 9, 11), EnrollmentStatus.WITHDRAWN);
        enroll(section(DayOfWeek.MONDAY, 9, 11), EnrollmentStatus.WAITLISTED);

        assertThat(check(target)).isEmpty();
    }

    @Test
    void firstConflictingEnrollmentInIdOrderIsTheOneReported() {
        SubjectSection target = section(DayOfWeek.MONDAY, 9, 11);
        enroll(section(DayOfWeek.TUESDAY, 9, 11), EnrollmentStatus.ENROLLED);
        SubjectSection conflicting = section(DayOfWeek.MONDAY, 10, 12);
        enroll(conflicting, EnrollmentStatus.ENROLLED);

        Optional<SectionScheduleOverlapException> overlap = check(target);

        assertThat(overlap).isPresent();
        assertThat(overlap.get().getMessage()).contains(conflicting.getSubject().getName()).contains("10:00");
    }

    @Test
    void queryCountDoesNotGrowWithActiveEnrollments() {
        long withFew = countQueriesWith(FEW);
        long withMany = countQueriesWith(MANY);

        assertThat(withMany).isEqualTo(withFew);
    }

    private long countQueriesWith(int enrollments) {
        newStudent();
        SubjectSection target = section(DayOfWeek.MONDAY, 8, 9);
        for (int i = 0; i < enrollments; i++) {
            enroll(section(DayOfWeek.TUESDAY, 8, 9), EnrollmentStatus.ENROLLED);
        }
        fixtures.flushAndClear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        Optional<SectionScheduleOverlapException> overlap = guard.findOverlapForProgramSection(student.getId(), target.getId(), term.getId(), null);
        long queries = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        assertThat(overlap).isEmpty();
        return queries;
    }
}
