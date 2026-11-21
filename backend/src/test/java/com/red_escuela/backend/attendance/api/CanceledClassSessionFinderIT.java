package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CanceledClassSessionFinderIT extends AbstractIntegrationTest {

    private static final LocalDate FROM = LocalDate.of(2160, 4, 10);
    private static final LocalDate TO = LocalDate.of(2160, 4, 20);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CanceledClassSessionFinder finder;

    private CycleTestFixtures fixtures;
    private Term term;
    private Instructor creator;
    private SubjectSection programSection;
    private SubjectSection otherProgramSection;
    private CourseSubjectSection courseSection;

    @BeforeEach
    void seedSections() {
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(2160);
        creator = fixtures.instructor();
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        programSection = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        otherProgramSection = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        courseSection = courseSection();
    }

    @Test
    void onlyCanceledProgramSessionsInsideTheInclusiveRangeAreReturned() {
        programSession(programSection, FROM.minusDays(1), ClassSessionStatus.CANCELED);
        programSession(programSection, FROM, ClassSessionStatus.CANCELED);
        programSession(programSection, FROM.plusDays(3), ClassSessionStatus.HELD);
        programSession(programSection, FROM.plusDays(4), ClassSessionStatus.SCHEDULED);
        programSession(programSection, TO, ClassSessionStatus.CANCELED);
        programSession(programSection, TO.plusDays(1), ClassSessionStatus.CANCELED);
        entityManager.flush();
        entityManager.clear();

        assertThat(finder.findCanceled(SectionKind.PROGRAM, List.of(programSection.getId()), FROM, TO))
                .containsExactlyInAnyOrder(
                        new CanceledClassView(programSection.getId(), FROM),
                        new CanceledClassView(programSection.getId(), TO));
    }

    @Test
    void onlyRequestedSectionsAreReturnedInOneBatch() {
        programSession(programSection, FROM.plusDays(1), ClassSessionStatus.CANCELED);
        programSession(otherProgramSection, FROM.plusDays(2), ClassSessionStatus.CANCELED);
        entityManager.flush();
        entityManager.clear();

        assertThat(finder.findCanceled(SectionKind.PROGRAM, List.of(programSection.getId()), FROM, TO))
                .containsExactly(new CanceledClassView(programSection.getId(), FROM.plusDays(1)));
        assertThat(finder.findCanceled(SectionKind.PROGRAM,
                List.of(programSection.getId(), otherProgramSection.getId()), FROM, TO)).hasSize(2);
    }

    @Test
    void courseSessionsAreFoundByTheCourseKindOnly() {
        courseSession(courseSection, FROM.plusDays(5), ClassSessionStatus.CANCELED);
        courseSession(courseSection, FROM.plusDays(6), ClassSessionStatus.HELD);
        programSession(programSection, FROM.plusDays(5), ClassSessionStatus.CANCELED);
        entityManager.flush();
        entityManager.clear();

        assertThat(finder.findCanceled(SectionKind.COURSE, List.of(courseSection.getId()), FROM, TO))
                .containsExactly(new CanceledClassView(courseSection.getId(), FROM.plusDays(5)));
        assertThat(finder.findCanceled(SectionKind.PROGRAM, List.of(programSection.getId()), FROM, TO))
                .containsExactly(new CanceledClassView(programSection.getId(), FROM.plusDays(5)));
    }

    @Test
    void emptySectionIdsReturnNothing() {
        assertThat(finder.findCanceled(SectionKind.PROGRAM, List.of(), FROM, TO)).isEmpty();
        assertThat(finder.findCanceled(SectionKind.COURSE, List.of(), FROM, TO)).isEmpty();
    }

    private void programSession(SubjectSection section, LocalDate date, ClassSessionStatus status) {
        ClassSession session = new ClassSession();
        session.setSubjectSection(section);
        session.setClassDate(date);
        session.setStatus(status);
        session.setCreatedBy(creator);
        entityManager.persist(session);
    }

    private void courseSession(CourseSubjectSection section, LocalDate date, ClassSessionStatus status) {
        ClassSession session = new ClassSession();
        session.setCourseSubjectSection(section);
        session.setClassDate(date);
        session.setStatus(status);
        session.setCreatedBy(creator);
        entityManager.persist(session);
    }

    private CourseSubjectSection courseSection() {
        Course course = new Course();
        course.setName("Curso 2160");
        course.setTerm(term);
        course.setCapacity(30);
        course.setIsActive(StatusType.ACTIVE);
        course.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(course);
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Módulo 2160");
        module.setStatus(StatusType.ACTIVE);
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comisión curso");
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }
}
