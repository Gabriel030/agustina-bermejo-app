package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SectionScheduleSlotOccupancyIT extends AbstractIntegrationTest {

    @Autowired
    private SectionScheduleSlotRepository slotRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Term termA;
    private Term termB;
    private Term termOverlappingA;
    private Classroom classroomOne;
    private Classroom classroomTwo;
    private AcademicOfferingCycle approvedCycle;
    private AcademicOfferingCycle draftCycle;
    private Long activeSectionSlotId;
    private Long inactiveSectionSlotId;
    private Long reactivatedSectionSlotId;
    private Long inactiveSectionInDraftCycleSlotId;
    private Long deletedSectionSlotId;
    private Long otherTermSlotId;
    private Long overlappingTermSlotId;
    private Long activeCourseSlotId;
    private Long inactiveCourseSlotId;
    private Long activeSectionId;
    private Long reactivatedSectionId;
    private Long activeCourseSectionId;

    @BeforeEach
    void seedDataset() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            termA = fixtures.term(2041);
            termB = fixtures.term(2043);
            termOverlappingA = fixtures.term(2041);
            termOverlappingA.setStartDate(LocalDateTime.of(2041, 12, 1, 0, 0));
            termOverlappingA.setEndDate(LocalDateTime.of(2042, 2, 1, 0, 0));
            var program = fixtures.program();
            approvedCycle = fixtures.programCycle(program, termA, AcademicOfferingCycleStatus.APPROVED);
            draftCycle = fixtures.programCycle(fixtures.program(), termA, AcademicOfferingCycleStatus.DRAFT);
            classroomOne = fixtures.classroom(30);
            classroomTwo = fixtures.classroom(30);
            var subject = fixtures.subject();

            SubjectSection active = fixtures.section(subject, termA, StatusType.ACTIVE, approvedCycle);
            SubjectSection inactive = fixtures.section(subject, termA, StatusType.INACTIVE, approvedCycle);
            SubjectSection reactivated = fixtures.section(subject, termA, StatusType.INACTIVE, approvedCycle);
            SubjectSection inactiveInDraft = fixtures.section(subject, termA, StatusType.INACTIVE, draftCycle);
            SubjectSection deleted = fixtures.section(subject, termA, StatusType.DELETED, approvedCycle);
            SubjectSection otherTerm = fixtures.section(subject, termB, StatusType.ACTIVE, null);
            SubjectSection overlapping = fixtures.section(subject, termOverlappingA, StatusType.ACTIVE, null);

            activeSectionSlotId = fixtures.slot(active, classroomOne, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)).getId();
            inactiveSectionSlotId = fixtures.slot(inactive, classroomOne, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)).getId();
            reactivatedSectionSlotId = fixtures.slot(reactivated, classroomTwo, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)).getId();
            inactiveSectionInDraftCycleSlotId = fixtures.slot(inactiveInDraft, classroomTwo, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)).getId();
            deletedSectionSlotId = fixtures.slot(deleted, classroomOne, DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)).getId();
            otherTermSlotId = fixtures.slot(otherTerm, classroomOne, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)).getId();
            overlappingTermSlotId = fixtures.slot(overlapping, classroomTwo, DayOfWeek.MONDAY, LocalTime.of(8, 30), LocalTime.of(9, 30)).getId();
            activeSectionId = active.getId();
            reactivatedSectionId = reactivated.getId();
        });
        Long activeCourseSectionIdValue = persistCourseSection(StatusType.ACTIVE);
        Long inactiveCourseSectionId = persistCourseSection(StatusType.INACTIVE);
        activeCourseSectionId = activeCourseSectionIdValue;
        activeCourseSlotId = insertCourseSlot(activeCourseSectionId, "THURSDAY", "08:00", "10:00");
        inactiveCourseSlotId = insertCourseSlot(inactiveCourseSectionId, "THURSDAY", "08:00", "10:00");
    }

    private Long persistCourseSection(StatusType status) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Course course = new Course();
            course.setName("Curso " + UUID.randomUUID());
            course.setTerm(termA);
            course.setCapacity(30);
            course.setIsActive(StatusType.ACTIVE);
            course.setTeachingMode(TeachingMode.ONLINE);
            entityManager.persist(course);
            CourseSubject module = new CourseSubject();
            module.setCourse(course);
            module.setName("Modulo");
            module.setStatus(StatusType.ACTIVE);
            entityManager.persist(module);
            CourseSubjectSection section = new CourseSubjectSection();
            section.setName("Comision curso");
            section.setCourseSubject(module);
            section.setTerm(termA);
            section.setCapacity(20);
            section.setStatus(status);
            entityManager.persist(section);
            entityManager.flush();
            return section.getId();
        });
    }

    private Long insertCourseSlot(Long courseSectionId, String day, String start, String end) {
        return jdbc.queryForObject(
                "insert into section_schedule_slot (course_section_id, classroom_id, day_of_week, start_time, end_time) "
                        + "values (?, ?, ?, ?::time, ?::time) returning id",
                Long.class, courseSectionId, classroomOne.getId(), day, start, end);
    }

    private Set<Long> occupyingIds() {
        return new TreeSet<>(jdbc.queryForList("select id from section_schedule_slot where occupies", Long.class));
    }

    private boolean occupies(Long slotId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select occupies from section_schedule_slot where id = ?", Boolean.class, slotId));
    }

    private Set<Long> occupyingIdsBySql(Term term, DayOfWeek day, LocalTime start, LocalTime end) {
        return new TreeSet<>(jdbc.queryForList(
                "select id from section_schedule_slot where occupies and day_of_week = ? "
                        + "and start_time < ?::time and end_time > ?::time and term_period && tsrange(?, ?, '[]')",
                Long.class, day.name(), end.toString(), start.toString(),
                Timestamp.valueOf(term.getStartDate()), Timestamp.valueOf(term.getEndDate())));
    }

    private Set<Long> occupyingIdsByRepository(Term term, DayOfWeek day, LocalTime start, LocalTime end) {
        var ids = new TreeSet<Long>();
        slotRepository.findOccupyingSlots(term.getId(), term.getStartDate(), term.getEndDate(), day, start, end)
                .forEach(slot -> ids.add(slot.getId()));
        return ids;
    }

    private void assertEquivalentToRepositoryForEveryWindow() {
        List<LocalTime[]> windows = List.of(
                new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(10, 0)},
                new LocalTime[]{LocalTime.of(10, 0), LocalTime.of(12, 0)},
                new LocalTime[]{LocalTime.of(9, 30), LocalTime.of(10, 30)},
                new LocalTime[]{LocalTime.of(0, 0), LocalTime.of(23, 59)},
                new LocalTime[]{LocalTime.of(12, 0), LocalTime.of(13, 0)});
        var compared = new ArrayList<Set<Long>>();
        for (Term term : List.of(termA, termB, termOverlappingA)) {
            for (DayOfWeek day : DayOfWeek.values()) {
                for (LocalTime[] window : windows) {
                    var viaRepository = occupyingIdsByRepository(term, day, window[0], window[1]);
                    assertThat(occupyingIdsBySql(term, day, window[0], window[1]))
                            .as("term %s day %s window %s", term.getId(), day, window[0])
                            .isEqualTo(viaRepository);
                    compared.add(viaRepository);
                }
            }
        }
        assertThat(compared).anySatisfy(ids -> assertThat(ids).isNotEmpty());
    }

    @Test
    void occupiesMatchesFindOccupyingSlotsAcrossStatusesCyclesAndTerms() {
        assertThat(occupyingIds()).contains(activeSectionSlotId, inactiveSectionInDraftCycleSlotId,
                otherTermSlotId, overlappingTermSlotId, activeCourseSlotId);
        assertThat(occupyingIds()).doesNotContain(inactiveSectionSlotId, reactivatedSectionSlotId,
                deletedSectionSlotId, inactiveCourseSlotId);

        assertEquivalentToRepositoryForEveryWindow();
    }

    @Test
    void occupiesStaysEquivalentAfterReactivatingAndDeactivatingSections() {
        jdbc.update("update subject_section set status = 'ACTIVE' where id = ?", reactivatedSectionId);
        jdbc.update("update subject_section set status = 'INACTIVE' where id = ?", activeSectionId);

        assertEquivalentToRepositoryForEveryWindow();
    }

    @Test
    void insertingASlotForAnActiveSectionMarksItAsOccupyingWithItsDerivedColumns() {
        Long slotId = jdbc.queryForObject(
                "insert into section_schedule_slot (subject_section_id, classroom_id, day_of_week, start_time, end_time) "
                        + "values (?, ?, 'FRIDAY', '08:00', '10:00') returning id",
                Long.class, activeSectionId, classroomOne.getId());

        assertThat(occupies(slotId)).isTrue();
        assertThat(jdbc.queryForObject("select time_span::text from section_schedule_slot where id = ?", String.class, slotId))
                .isEqualTo("[374400,381600)");
        assertThat(jdbc.queryForObject("select term_period && tsrange(?, ?, '[]') from section_schedule_slot where id = ?",
                Boolean.class, Timestamp.valueOf(termA.getStartDate()), Timestamp.valueOf(termA.getEndDate()), slotId)).isTrue();
    }

    @Test
    void changingTheScheduleRecomputesTheTimeSpan() {
        jdbc.update("update section_schedule_slot set day_of_week = 'SUNDAY', start_time = '18:00', end_time = '19:00' where id = ?",
                activeSectionSlotId);

        assertThat(jdbc.queryForObject("select time_span::text from section_schedule_slot where id = ?", String.class, activeSectionSlotId))
                .isEqualTo("[583200,586800)");
    }

    @Test
    void deactivatingASectionStopsItsSlotsFromOccupying() {
        jdbc.update("update subject_section set status = 'INACTIVE' where id = ?", activeSectionId);

        assertThat(occupies(activeSectionSlotId)).isFalse();
    }

    @Test
    void cancellingASectionStopsItsSlotsFromOccupying() {
        jdbc.update("update subject_section set status = 'DELETED' where id = ?", activeSectionId);

        assertThat(occupies(activeSectionSlotId)).isFalse();
    }

    @Test
    void reactivatingAnInactiveSectionMakesItsSlotsOccupyAgain() {
        assertThat(occupies(reactivatedSectionSlotId)).isFalse();

        jdbc.update("update subject_section set status = 'ACTIVE' where id = ?", reactivatedSectionId);

        assertThat(occupies(reactivatedSectionSlotId)).isTrue();
    }

    @Test
    void deactivatingACourseSectionStopsItsSlotsFromOccupying() {
        jdbc.update("update course_section set status = 'INACTIVE' where id = ?", activeCourseSectionId);

        assertThat(occupies(activeCourseSlotId)).isFalse();
    }

    @Test
    void movingASectionToAnotherTermUpdatesTheTermPeriodOfItsSlots() {
        jdbc.update("update subject_section set term_id = ? where id = ?", termB.getId(), activeSectionId);

        assertThat(jdbc.queryForObject("select term_period && tsrange(?, ?, '[]') from section_schedule_slot where id = ?",
                Boolean.class, Timestamp.valueOf(termB.getStartDate()), Timestamp.valueOf(termB.getEndDate()), activeSectionSlotId)).isTrue();
        assertThat(jdbc.queryForObject("select term_period && tsrange(?, ?, '[]') from section_schedule_slot where id = ?",
                Boolean.class, Timestamp.valueOf(termA.getStartDate()), Timestamp.valueOf(termA.getEndDate()), activeSectionSlotId)).isFalse();
    }

    @Test
    void changingATermDatesUpdatesTheTermPeriodOfItsSlots() {
        jdbc.update("update term set end_date = ? where id = ?", Timestamp.valueOf(LocalDateTime.of(2041, 4, 1, 0, 0)), termA.getId());

        assertThat(jdbc.queryForObject("select upper(term_period) from section_schedule_slot where id = ?",
                Timestamp.class, activeSectionSlotId)).isEqualTo(Timestamp.valueOf(LocalDateTime.of(2041, 4, 1, 0, 0)));
    }

    @Test
    void cycleStatusChangeAffectsSectionsWithoutActiveStatus() {
        assertThat(occupies(inactiveSectionInDraftCycleSlotId)).isTrue();

        jdbc.update("update academic_offering_cycle set status = 'APPROVED' where id = ?", draftCycle.getId());

        assertThat(occupies(inactiveSectionInDraftCycleSlotId)).isFalse();
    }

    @Test
    void fractionalSecondTimesAreTruncatedInTheTimeSpanAndStayEquivalent() {
        Long slotId = jdbc.queryForObject(
                "insert into section_schedule_slot (subject_section_id, classroom_id, day_of_week, start_time, end_time) "
                        + "values (?, ?, 'MONDAY', '08:00:00.600', '10:00:00.600') returning id",
                Long.class, activeSectionId, classroomOne.getId());

        assertThat(jdbc.queryForObject("select time_span::text from section_schedule_slot where id = ?", String.class, slotId))
                .isEqualTo("[28800,36000)");
        assertThat(occupyingIdsBySql(termA, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isEqualTo(occupyingIdsByRepository(termA, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .contains(slotId);
        assertEquivalentToRepositoryForEveryWindow();
    }

    @Test
    void knownGapForS3ATermWithEndBeforeStartCannotExistSoTermPeriodIsNeverNullForRealSections() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) "
                        + "values (2050, 'ACTIVE', '2050-12-01', '2050-03-01', '2050-01-01', '2050-02-01')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot where term_period is null", Integer.class)).isZero();
    }

    @Test
    void knownGapForS3ASlotWithoutAnOwningSectionHasNullTermPeriodAndDoesNotOccupy() {
        assertThat(jdbc.queryForObject("select section_schedule_slot_term_period(null, null) is null", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select section_schedule_slot_occupies(null, null)", Boolean.class)).isFalse();
    }
}
