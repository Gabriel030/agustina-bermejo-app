package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.api.PersonAudienceFinder;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentQuantifier;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCriterionRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.audience.specialist.SegmentResolver;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SegmentStrategiesIT extends AbstractIntegrationTest {

    @Autowired
    private SegmentResolver segmentResolver;

    @Autowired
    private PersonAudienceFinder personAudienceFinder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    private CycleTestFixtures fixtures;
    private Institution institution;
    private Program program;
    private Subject subject;
    private SubjectSection section;
    private ProgramEnrollment programEnrollment;

    private Student inCourse;
    private Student regular;
    private Student graduated;
    private Student withdrawn;
    private Student inactivePerson;

    @BeforeEach
    void seedStudents() {
        fixtures = new CycleTestFixtures(entityManager);
        institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Term term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
        program = fixtures.program();
        subject = fixtures.subject();
        var cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);

        inCourse = student();
        regular = student();
        graduated = student();
        withdrawn = student();
        inactivePerson = student();
        inactivePerson.getPerson().setStatus(StatusType.DELETED);

        programEnrollmentOf(inCourse, ProgramEnrollmentStatus.ENROLLED);
        programEnrollmentOf(regular, ProgramEnrollmentStatus.ENROLLED);
        programEnrollmentOf(graduated, ProgramEnrollmentStatus.COMPLETED);
        programEnrollmentOf(withdrawn, ProgramEnrollmentStatus.WITHDRAWN);
        programEnrollmentOf(inactivePerson, ProgramEnrollmentStatus.ENROLLED);

        sectionEnrollmentOf(inCourse, EnrollmentStatus.IN_PROGRESS);
        sectionEnrollmentOf(regular, EnrollmentStatus.REGULAR);
        sectionEnrollmentOf(withdrawn, EnrollmentStatus.WITHDRAWN);
        sectionEnrollmentOf(inactivePerson, EnrollmentStatus.IN_PROGRESS);
        entityManager.flush();
    }

    private Student student() {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private void programEnrollmentOf(Student student, ProgramEnrollmentStatus status) {
        programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(status);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
    }

    private void sectionEnrollmentOf(Student student, EnrollmentStatus status) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(entityManager.createQuery(
                "select p from ProgramEnrollment p where p.student = :student", ProgramEnrollment.class)
                .setParameter("student", student).getSingleResult());
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
    }

    private Set<Long> resolve(SegmentKey key, String value) {
        return segmentResolver.resolvePersonIds(new SegmentSpecRequest(1, SegmentQuantifier.ALL,
                List.of(new SegmentCriterionRequest(key, value))));
    }

    private Set<Long> mine(Set<Long> resolved) {
        Set<Long> mine = new HashSet<>(resolved);
        mine.retainAll(Set.of(inCourse.getPerson().getId(), regular.getPerson().getId(), graduated.getPerson().getId(),
                withdrawn.getPerson().getId(), inactivePerson.getPerson().getId()));
        return mine;
    }

    @Test
    void programCriterionReturnsExactlyTheActiveEnrolledStudents() {
        assertThat(resolve(SegmentKey.PROGRAM, program.getId().toString()))
                .containsExactlyInAnyOrder(inCourse.getPerson().getId(), regular.getPerson().getId());
    }

    @Test
    void subjectCriterionReturnsExactlyTheStudentsInCourseOfThatSubject() {
        assertThat(resolve(SegmentKey.SUBJECT, subject.getId().toString()))
                .containsExactly(inCourse.getPerson().getId());
    }

    @Test
    void enrollmentStatusInCourseExcludesWithdrawnAndInactivePeople() {
        assertThat(mine(resolve(SegmentKey.ENROLLMENT_STATUS, "IN_COURSE"))).containsExactly(inCourse.getPerson().getId());
    }

    @Test
    void enrollmentStatusRegularReturnsOnlyRegularStudents() {
        assertThat(mine(resolve(SegmentKey.ENROLLMENT_STATUS, "REGULAR"))).containsExactly(regular.getPerson().getId());
    }

    @Test
    void enrollmentStatusGraduatedReturnsOnlyGraduatedStudents() {
        assertThat(mine(resolve(SegmentKey.ENROLLMENT_STATUS, "GRADUATED"))).containsExactly(graduated.getPerson().getId());
    }

    @Test
    void enrollmentStatusWithdrawnReturnsOnlyWithdrawnStudents() {
        assertThat(mine(resolve(SegmentKey.ENROLLMENT_STATUS, "WITHDRAWN"))).containsExactly(withdrawn.getPerson().getId());
    }

    @Test
    void staffRoleCriterionExcludesInactiveStaff() {
        Person active = AudienceTestPersons.create(entityManager, roleRepository, "seg-active-teacher", true, null, "DOCENTE");
        Person inactive = AudienceTestPersons.create(entityManager, roleRepository, "seg-inactive-teacher", true, null, "DOCENTE");
        inactive.setStatus(StatusType.INACTIVE);
        entityManager.flush();

        assertThat(resolve(SegmentKey.STAFF_ROLE, "DOCENTE")).contains(active.getId()).doesNotContain(inactive.getId());
    }

    @Test
    void overdueDebtPeopleAreMappedFromStudentsKeepingOnlyActivePeople() {
        assertThat(personAudienceFinder.personIdsOfStudents(List.of(inCourse.getId(), inactivePerson.getId())))
                .containsExactly(inCourse.getPerson().getId());
    }
}
