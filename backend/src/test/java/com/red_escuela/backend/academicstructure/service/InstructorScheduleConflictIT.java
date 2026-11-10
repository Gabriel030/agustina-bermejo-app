package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
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
import com.red_escuela.backend.academicstructure.usecase.cycle.ApproveCycleUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.ApproveSectionModificationUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.RequestSectionModificationUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstructorScheduleConflictIT extends AbstractIntegrationTest {

    private static final LocalTime SEVEN_PM = LocalTime.of(19, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);
    private static final LocalTime ELEVEN_PM = LocalTime.of(23, 0);

    @Autowired
    private SubjectSectionService subjectSectionService;
    @Autowired
    private CourseSectionService courseSectionService;
    @Autowired
    private CourseSubjectService courseSubjectService;
    @Autowired
    private RequestSectionModificationUseCase requestModification;
    @Autowired
    private ApproveSectionModificationUseCase approveModification;
    @Autowired
    private ApproveCycleUseCase approveCycle;
    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;
    private Term term;
    private Instructor teacher;
    private Classroom roomA;
    private Classroom roomB;

    @BeforeEach
    void seed() {
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
        teacher = fixtures.instructor();
        roomA = fixtures.classroom(30);
        roomB = fixtures.classroom(30);
    }

    private AssignInstructorRequest assignment(Instructor instructor) {
        return new AssignInstructorRequest(instructor.getId(), InstructorRole.LEAD, null, null, null, null);
    }

    private SubjectSection teachingSection(Term sectionTerm, StatusType status, Classroom room, DayOfWeek day, LocalTime start, LocalTime end) {
        SubjectSection section = fixtures.section(fixtures.subject(), sectionTerm, status, null);
        fixtures.slot(section, room, day, start, end);
        fixtures.assignLead(section, teacher);
        return section;
    }

    private SubjectSection candidateSection(Classroom room, DayOfWeek day, LocalTime start, LocalTime end) {
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(section, room, day, start, end);
        return section;
    }

    private CourseSubjectSection courseSection(DayOfWeek day, LocalTime start, LocalTime end, Classroom room) {
        Course course = Course.builder().name("Curso " + System.nanoTime()).term(term)
                .capacity(10).durationMonths(3).isActive(StatusType.ACTIVE)
                .currency(com.red_escuela.backend.shared.domain.Currency.ARS).build();
        entityManager.persist(course);
        CourseSubject module = courseSubjectService.addModule(course.getId(),
                new CreateCourseSubjectRequest("Modulo", 1, 4, SubjectRecurrence.ANNUAL, "NUMERIC"));
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision curso " + System.nanoTime());
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setCapacity(10);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setCourseSection(section);
        slot.setClassroom(room);
        slot.setDayOfWeek(day);
        slot.setStartTime(start);
        slot.setEndTime(end);
        entityManager.persist(slot);
        return section;
    }

    @Test
    void assigningATeacherAlreadyTeachingAtTheSameTimeIsRejectedWithAStableConflictCode() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .isInstanceOf(InstructorScheduleConflictException.class)
                .isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(ErrorCodeResponse.codeOf(error.getClass())).isEqualTo("INSTRUCTOR_SCHEDULE_CONFLICT"));
    }

    @Test
    void partiallyOverlappingSlotsConflict() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.MONDAY, LocalTime.of(20, 0), ELEVEN_PM);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void slotsThatOnlyTouchAtTheBoundaryDoNotConflict() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.MONDAY, NINE_PM, ELEVEN_PM);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .doesNotThrowAnyException();
    }

    @Test
    void sameHoursOnAnotherWeekdayDoNotConflict() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.TUESDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .doesNotThrowAnyException();
    }

    @Test
    void anotherTeacherAtTheSameTimeDoesNotConflict() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        Instructor otherTeacher = fixtures.instructor();
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(otherTeacher)))
                .doesNotThrowAnyException();
    }

    @Test
    void sameHoursInAnotherAcademicYearDoNotConflict() {
        Term otherYear = fixtures.term(2100 + (int) (System.nanoTime() % 50));
        teachingSection(otherYear, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .doesNotThrowAnyException();
    }

    @Test
    void inactiveSectionsOutsideAnOpenCycleDoNotBlockTheTeacher() {
        teachingSection(term, StatusType.INACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .doesNotThrowAnyException();
    }

    @Test
    void sectionsOfADraftCycleBlockTheTeacher() {
        var draft = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        SubjectSection draftSection = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, draft);
        fixtures.slot(draftSection, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.assignLead(draftSection, teacher);
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void aCourseSectionAndAProgramSectionAtTheSameTimeConflictInBothDirections() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.WEDNESDAY, SEVEN_PM, NINE_PM);
        CourseSubjectSection course = courseSection(DayOfWeek.WEDNESDAY, SEVEN_PM, NINE_PM, roomB);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> courseSectionService.assignInstructor(course.getId(), assignment(teacher)))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void aTeacherOfACourseSectionCannotBeAssignedToAProgramSectionAtTheSameTime() {
        CourseSubjectSection course = courseSection(DayOfWeek.THURSDAY, SEVEN_PM, NINE_PM, roomA);
        courseSectionService.assignInstructor(course.getId(), assignment(teacher));
        SubjectSection candidate = candidateSection(roomB, DayOfWeek.THURSDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> subjectSectionService.assignInstructor(candidate.getId(), assignment(teacher)))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void anotherCommissionOfTheSameCourseAtTheSameTimeConflicts() {
        CourseSubjectSection first = courseSection(DayOfWeek.FRIDAY, SEVEN_PM, NINE_PM, roomA);
        CourseSubjectSection second = courseSection(DayOfWeek.FRIDAY, SEVEN_PM, NINE_PM, roomB);
        courseSectionService.assignInstructor(first.getId(), assignment(teacher));
        fixtures.flushAndClear();

        assertThatThrownBy(() -> courseSectionService.assignInstructor(second.getId(), assignment(teacher)))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void assigningATeacherToASectionWithoutSlotsIsAllowed() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection withoutSlots = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.flushAndClear();

        assertThatCode(() -> subjectSectionService.assignInstructor(withoutSlots.getId(), assignment(teacher)))
                .doesNotThrowAnyException();
    }

    @Test
    void proposingAScheduleThatClashesWithTheSectionTeacherOtherCommissionIsRejected() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        SubjectSection own = candidateSection(roomB, DayOfWeek.TUESDAY, SEVEN_PM, NINE_PM);
        fixtures.assignLead(own, teacher);
        fixtures.flushAndClear();

        var clashing = new ModifySectionRequest(List.of(
                new ScheduleSlotRequest(roomB.getId(), DayOfWeek.MONDAY, LocalTime.of(20, 0), ELEVEN_PM)));

        assertThatThrownBy(() -> requestModification.requestModification(own.getId(), clashing))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void keepingTheOwnCurrentSlotInAModificationIsNotAConflictWithItself() {
        SubjectSection own = teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        var sameSlot = new ModifySectionRequest(List.of(
                new ScheduleSlotRequest(roomA.getId(), DayOfWeek.MONDAY, SEVEN_PM, NINE_PM)));

        assertThatCode(() -> requestModification.requestModification(own.getId(), sameSlot))
                .doesNotThrowAnyException();
    }

    @Test
    void approvingAModificationRechecksTheTeacherAgainstCommissionsCreatedAfterTheRequest() {
        SubjectSection own = teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();
        requestModification.requestModification(own.getId(), new ModifySectionRequest(List.of(
                new ScheduleSlotRequest(roomB.getId(), DayOfWeek.TUESDAY, SEVEN_PM, NINE_PM))));
        teachingSection(term, StatusType.ACTIVE, fixtures.classroom(30), DayOfWeek.TUESDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> approveModification.approveModification(own.getId()))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }

    @Test
    void approvingACycleWhoseSectionTeacherClashesWithAnActiveCommissionIsRejected() {
        teachingSection(term, StatusType.ACTIVE, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        SubjectSection pending = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.slot(pending, roomB, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.assignLead(pending, teacher);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> approveCycle.approve(cycle.getId(), null))
                .isInstanceOf(InstructorScheduleConflictException.class);
    }
}
