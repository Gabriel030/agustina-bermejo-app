package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentActiveSectionsReaderIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private StudentActiveSectionsReader reader;

    private CycleTestFixtures fixtures;
    private Institution institution;
    private Term term;
    private Student student;
    private Student otherStudent;
    private ProgramEnrollment programEnrollment;
    private ProgramEnrollment otherProgramEnrollment;
    private CourseEnrollment courseEnrollment;
    private Course course;

    @BeforeEach
    void seedStudentsAndEnrollments() {
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(2100 + Math.floorMod(System.nanoTime(), 50));
        institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        student = student();
        otherStudent = student();
        var program = fixtures.program();
        programEnrollment = programEnrollment(student, program);
        otherProgramEnrollment = programEnrollment(otherStudent, program);
        course = course();
        courseEnrollment = courseEnrollment(student);
    }

    @Test
    void programSectionsInActiveStatusesAreListedWithSubjectAndTerm() {
        Map<EnrollmentStatus, SubjectSection> sections = new EnumMap<>(EnrollmentStatus.class);
        for (EnrollmentStatus status : EnrollmentStatus.values()) {
            sections.put(status, programEnrollmentIn(student, programEnrollment, status));
        }
        entityManager.flush();
        entityManager.clear();

        List<ActiveSectionView> views = reader.listActiveSectionsOfStudent(student.getId());

        assertThat(views).extracting(ActiveSectionView::sectionId).containsExactlyInAnyOrder(
                sections.get(EnrollmentStatus.ENROLLED).getId(),
                sections.get(EnrollmentStatus.IN_PROGRESS).getId(),
                sections.get(EnrollmentStatus.REGULAR).getId());
        SubjectSection enrolled = sections.get(EnrollmentStatus.ENROLLED);
        assertThat(views).contains(new ActiveSectionView(SectionKind.PROGRAM, enrolled.getId(), enrolled.getName(),
                enrolled.getSubject().getName(), term.getId()));
    }

    @Test
    void waitlistedWithdrawnApprovedAndFailedProgramSectionsAreExcluded() {
        programEnrollmentIn(student, programEnrollment, EnrollmentStatus.WAITLISTED);
        programEnrollmentIn(student, programEnrollment, EnrollmentStatus.WITHDRAWN);
        programEnrollmentIn(student, programEnrollment, EnrollmentStatus.APPROVED);
        programEnrollmentIn(student, programEnrollment, EnrollmentStatus.FAILED);
        entityManager.flush();

        assertThat(reader.listActiveSectionsOfStudent(student.getId())).isEmpty();
    }

    @Test
    void courseSectionsInActiveStatusesAreListedAndInactiveOnesExcluded() {
        CourseSubjectSection enrolled = courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.ENROLLED);
        CourseSubjectSection inProgress = courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.IN_PROGRESS);
        CourseSubjectSection regular = courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.REGULAR);
        courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.WAITLISTED);
        courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.WITHDRAWN);
        courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.APPROVED);
        courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.FAILED);
        entityManager.flush();
        entityManager.clear();

        List<ActiveSectionView> views = reader.listActiveSectionsOfStudent(student.getId());

        assertThat(views).extracting(ActiveSectionView::kind).containsOnly(SectionKind.COURSE);
        assertThat(views).extracting(ActiveSectionView::sectionId)
                .containsExactlyInAnyOrder(enrolled.getId(), inProgress.getId(), regular.getId());
        assertThat(views).extracting(ActiveSectionView::subjectName)
                .containsExactlyInAnyOrder(enrolled.getCourseSubject().getName(),
                        inProgress.getCourseSubject().getName(), regular.getCourseSubject().getName());
    }

    @Test
    void programAndCourseSectionsAreReturnedTogether() {
        SubjectSection program = programEnrollmentIn(student, programEnrollment, EnrollmentStatus.IN_PROGRESS);
        CourseSubjectSection course = courseEnrollmentIn(student, courseEnrollment, EnrollmentStatus.ENROLLED);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listActiveSectionsOfStudent(student.getId()))
                .extracting(ActiveSectionView::kind, ActiveSectionView::sectionId)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(SectionKind.PROGRAM, program.getId()),
                        Tuple.tuple(SectionKind.COURSE, course.getId()));
    }

    @Test
    void aStudentNeverSeesTheSectionsOfAnotherStudent() {
        programEnrollmentIn(otherStudent, otherProgramEnrollment, EnrollmentStatus.ENROLLED);
        courseEnrollmentIn(otherStudent, courseEnrollment, EnrollmentStatus.ENROLLED);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listActiveSectionsOfStudent(student.getId())).isEmpty();
        assertThat(reader.listActiveSectionsOfStudent(otherStudent.getId())).hasSize(2);
    }

    private Student student() {
        Student created = new Student();
        created.setPerson(fixtures.person());
        created.setInstitution(institution);
        created.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(created);
        return created;
    }

    private ProgramEnrollment programEnrollment(Student owner, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(owner);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private SubjectSection programEnrollmentIn(Student owner, ProgramEnrollment parent, EnrollmentStatus status) {
        var cycle = fixtures.programCycle(parent.getProgram(), term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(owner);
        enrollment.setProgramEnrollment(parent);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return section;
    }

    private Course course() {
        Course created = new Course();
        created.setName("Curso " + term.getYear());
        created.setTerm(term);
        created.setCapacity(30);
        created.setIsActive(StatusType.ACTIVE);
        created.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(created);
        return created;
    }

    private CourseEnrollment courseEnrollment(Student owner) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(owner);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private CourseSubjectSection courseEnrollmentIn(Student owner, CourseEnrollment parent, EnrollmentStatus status) {
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Módulo " + System.nanoTime());
        module.setStatus(StatusType.ACTIVE);
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comisión " + System.nanoTime());
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(owner);
        enrollment.setCourseEnrollment(parent);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return section;
    }
}
