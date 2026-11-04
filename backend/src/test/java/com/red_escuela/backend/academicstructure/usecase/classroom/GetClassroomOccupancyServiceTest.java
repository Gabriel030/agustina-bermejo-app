package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.ClassroomOccupancyResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.InvalidDateRangeException;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GetClassroomOccupancyServiceTest extends AbstractIntegrationTest {

    @Autowired
    private GetClassroomOccupancyUseCase getOccupancy;

    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CycleTestFixtures(entityManager);
    }

    @Test
    void classroomWithoutSlotsHasEmptyOccupancy() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        fixtures.flushAndClear();

        ClassroomOccupancyResponse occupancy = getOccupancy.getOccupancy(classroom.getId(), term.getId(), null, null);

        assertThat(occupancy.slots()).isEmpty();
        assertThat(occupancy.capacity()).isEqualTo(30);
    }

    @Test
    void slotOfTheSameTermIsReportedWithRealOverlapAndSectionData() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(section, classroom, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));
        fixtures.flushAndClear();

        var slots = getOccupancy.getOccupancy(classroom.getId(), term.getId(), null, null).slots();

        assertThat(slots).hasSize(1);
        var slot = slots.get(0);
        assertThat(slot.overlapsInDates()).isTrue();
        assertThat(slot.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(slot.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(slot.section().id()).isEqualTo(section.getId());
        assertThat(slot.section().sectionName()).isEqualTo(section.getName());
        assertThat(slot.section().name()).isEqualTo(section.getSubject().getName());
        assertThat(slot.term().id()).isEqualTo(term.getId());
    }

    @Test
    void slotOfAnEarlierTermThatEndsBeforeMineIsInformativeWithoutOverlap() {
        Classroom classroom = fixtures.classroom(30);
        SubjectSection pastSection = fixtures.section(fixtures.subject(), fixtures.term(2025), StatusType.ACTIVE, null);
        fixtures.slot(pastSection, classroom, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        Term current = fixtures.term(2026);
        fixtures.flushAndClear();

        var slots = getOccupancy.getOccupancy(classroom.getId(), current.getId(), null, null).slots();

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).overlapsInDates()).isFalse();
    }

    @Test
    void draftCycleSectionCountsAndInactiveSectionDoesNot() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        AcademicOfferingCycle draft = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        SubjectSection draftSection = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, draft);
        fixtures.slot(draftSection, classroom, DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubjectSection inactive = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, null);
        fixtures.slot(inactive, classroom, DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        fixtures.flushAndClear();

        var slots = getOccupancy.getOccupancy(classroom.getId(), term.getId(), null, null).slots();

        assertThat(slots).extracting(s -> s.section().id()).containsExactly(draftSection.getId());
    }

    @Test
    void explicitRangeExcludesTermsOutsideIt() {
        Classroom classroom = fixtures.classroom(30);
        SubjectSection pastSection = fixtures.section(fixtures.subject(), fixtures.term(2025), StatusType.ACTIVE, null);
        fixtures.slot(pastSection, classroom, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        Term current = fixtures.term(2026);
        SubjectSection currentSection = fixtures.section(fixtures.subject(), current, StatusType.ACTIVE, null);
        fixtures.slot(currentSection, classroom, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(18, 0));
        fixtures.flushAndClear();

        var slots = getOccupancy.getOccupancy(classroom.getId(), current.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15)).slots();

        assertThat(slots).extracting(s -> s.section().id()).containsExactly(currentSection.getId());
    }

    @Test
    void slotsAreOrderedByDayAndStartTime() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(section, classroom, DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        fixtures.slot(section, classroom, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0));
        fixtures.slot(section, classroom, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        fixtures.flushAndClear();

        var slots = getOccupancy.getOccupancy(classroom.getId(), term.getId(), null, null).slots();

        assertThat(slots).extracting(s -> s.dayOfWeek() + "-" + s.startTime())
                .containsExactly("MONDAY-08:00", "MONDAY-14:00", "FRIDAY-08:00");
    }

    @Test
    void reversedRangeIsRejected() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> getOccupancy.getOccupancy(classroom.getId(), term.getId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void excessivelyWideRangeIsRejected() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> getOccupancy.getOccupancy(classroom.getId(), term.getId(), LocalDate.of(2000, 1, 1), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void unknownClassroomIsRejected() {
        Term term = fixtures.term(2026);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> getOccupancy.getOccupancy(-1L, term.getId(), null, null))
                .isInstanceOf(ClassroomNotFoundException.class);
    }

    @Test
    void queryCountDoesNotGrowWithTheNumberOfSlots() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        for (int i = 0; i < 8; i++) {
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
            fixtures.slot(section, classroom, DayOfWeek.values()[i % 5], LocalTime.of(8 + i, 0), LocalTime.of(9 + i, 0));
        }
        fixtures.flushAndClear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var slots = getOccupancy.getOccupancy(classroom.getId(), term.getId(), null, null).slots();

        assertThat(slots).hasSize(8);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }
}
