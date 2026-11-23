package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.RequesterAuthentication;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.InstructorScheduleConflictException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SectionReactivationAvailabilityIT extends AbstractIntegrationTest {

    private static final LocalTime SEVEN_PM = LocalTime.of(19, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);
    private static final LocalTime ELEVEN_PM = LocalTime.of(23, 0);
    private static final AtomicInteger TERM_YEARS = new AtomicInteger(3000);

    @Autowired
    private SubjectSectionService subjectSectionService;
    @Autowired
    private CourseSectionService courseSectionService;
    @Autowired
    private CourseSubjectService courseSubjectService;
    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;
    private Term term;
    private Instructor teacher;
    private Classroom roomA;
    private Classroom roomB;
    private AcademicOfferingCycle approvedCycle;

    @BeforeEach
    void seed() {
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(TERM_YEARS.getAndIncrement());
        teacher = fixtures.instructor();
        roomA = fixtures.classroom(30);
        roomB = fixtures.classroom(30);
        approvedCycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        RequesterAuthentication.authenticateNewRequester(entityManager);
    }

    @AfterEach
    void clearAuthentication() {
        RequesterAuthentication.clear();
    }

    private SubjectSection subjectSection(StatusType status, Classroom room, DayOfWeek day) {
        SubjectSection section = fixtures.section(fixtures.subject(), term, status, approvedCycle);
        fixtures.slot(section, room, day, SEVEN_PM, NINE_PM);
        return section;
    }

    private CourseSubjectSection courseSection(StatusType status, Classroom room, DayOfWeek day) {
        Course course = Course.builder().name("Curso " + System.nanoTime()).term(term)
                .capacity(10).durationMonths(3).isActive(StatusType.ACTIVE)
                .currency(Currency.ARS).build();
        entityManager.persist(course);
        CourseSubject module = courseSubjectService.addModule(course.getId(),
                new CreateCourseSubjectRequest("Modulo", 1, 4, SubjectRecurrence.ANNUAL, "NUMERIC"));
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision curso " + System.nanoTime());
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setCapacity(10);
        section.setStatus(status);
        entityManager.persist(section);
        if (room == null) {
            return section;
        }
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setCourseSection(section);
        slot.setClassroom(room);
        slot.setDayOfWeek(day);
        slot.setStartTime(SEVEN_PM);
        slot.setEndTime(NINE_PM);
        entityManager.persist(slot);
        return section;
    }

    @Test
    void reactivatingASubjectSectionWhoseClassroomWasTakenIsRejected() {
        SubjectSection original = subjectSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.flushAndClear();
        subjectSectionService.deactivate(original.getId());
        subjectSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> subjectSectionService.reactivate(original.getId()))
                .isInstanceOf(SectionScheduleConflictException.class);
        assertThat(subjectSectionService.get(original.getId()).getStatus()).isEqualTo(StatusType.INACTIVE);
    }

    @Test
    void reactivatingASubjectSectionWhoseTeacherIsBusyIsRejected() {
        SubjectSection original = subjectSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.assignLead(original, teacher);
        fixtures.flushAndClear();
        subjectSectionService.deactivate(original.getId());
        SubjectSection taker = subjectSection(StatusType.ACTIVE, roomB, DayOfWeek.MONDAY);
        fixtures.assignLead(taker, teacher);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> subjectSectionService.reactivate(original.getId()))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void reactivatingASubjectSectionWithFreeClassroomAndTeacherSucceeds() {
        SubjectSection original = subjectSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.assignLead(original, teacher);
        fixtures.flushAndClear();
        subjectSectionService.deactivate(original.getId());
        SubjectSection other = subjectSection(StatusType.ACTIVE, roomB, DayOfWeek.TUESDAY);
        fixtures.assignLead(other, teacher);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.reactivate(original.getId())).doesNotThrowAnyException();
        assertThat(subjectSectionService.get(original.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void reactivatingACourseSectionWhoseClassroomWasTakenIsRejected() {
        CourseSubjectSection original = courseSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.flushAndClear();
        courseSectionService.deactivate(original.getId());
        courseSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> courseSectionService.reactivate(original.getId()))
                .isInstanceOf(SectionScheduleConflictException.class);
        assertThat(courseSectionService.get(original.getId()).getStatus()).isEqualTo(StatusType.INACTIVE);
    }

    @Test
    void reactivatingACourseSectionWhoseTeacherIsBusyIsRejected() {
        CourseSubjectSection original = courseSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.flushAndClear();
        courseSectionService.assignInstructor(original.getId(), new AssignInstructorRequest(
                teacher.getId(), InstructorRole.LEAD, null, null, null, null));
        fixtures.flushAndClear();
        courseSectionService.deactivate(original.getId());
        SubjectSection taker = subjectSection(StatusType.ACTIVE, roomB, DayOfWeek.MONDAY);
        fixtures.assignLead(taker, teacher);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> courseSectionService.reactivate(original.getId()))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void reactivatingACourseSectionWithFreeClassroomSucceeds() {
        CourseSubjectSection original = courseSection(StatusType.ACTIVE, roomA, DayOfWeek.MONDAY);
        fixtures.flushAndClear();
        courseSectionService.deactivate(original.getId());
        courseSection(StatusType.ACTIVE, roomB, DayOfWeek.MONDAY);
        fixtures.flushAndClear();

        assertThatCode(() -> courseSectionService.reactivate(original.getId())).doesNotThrowAnyException();
        assertThat(courseSectionService.get(original.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void reactivatingASubjectSectionWithoutSlotsSucceeds() {
        SubjectSection legacy = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, approvedCycle);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.reactivate(legacy.getId())).doesNotThrowAnyException();
        assertThat(subjectSectionService.get(legacy.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void reactivatingACourseSectionWithoutSlotsSucceeds() {
        CourseSubjectSection legacy = courseSection(StatusType.INACTIVE, null, DayOfWeek.MONDAY);
        fixtures.flushAndClear();

        assertThatCode(() -> courseSectionService.reactivate(legacy.getId())).doesNotThrowAnyException();
        assertThat(courseSectionService.get(legacy.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void reactivatingASubjectSectionWithoutInstructorOnlyValidatesTheClassroom() {
        SubjectSection original = subjectSection(StatusType.INACTIVE, roomA, DayOfWeek.MONDAY);
        SubjectSection other = subjectSection(StatusType.ACTIVE, roomB, DayOfWeek.MONDAY);
        fixtures.assignLead(other, teacher);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.reactivate(original.getId())).doesNotThrowAnyException();
    }

    @Test
    void reactivatingASubjectSectionWhoseSlotOnlyTouchesAnotherAtTheBoundarySucceeds() {
        SubjectSection original = subjectSection(StatusType.INACTIVE, roomA, DayOfWeek.MONDAY);
        SubjectSection adjacent = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, approvedCycle);
        fixtures.slot(adjacent, roomA, DayOfWeek.MONDAY, NINE_PM, ELEVEN_PM);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.reactivate(original.getId())).doesNotThrowAnyException();
        assertThat(subjectSectionService.get(original.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }
}
