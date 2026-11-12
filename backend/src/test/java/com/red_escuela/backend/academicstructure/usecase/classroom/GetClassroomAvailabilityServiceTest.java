package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;
import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.testsupport.SlowTest;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SlowTest(reason = "When it is the first class of its shard its time includes the one-time start-up of the shared Spring context")
class GetClassroomAvailabilityServiceTest extends AbstractIntegrationTest {

    @Autowired
    private GetClassroomAvailabilityUseCase getAvailability;

    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CycleTestFixtures(entityManager);
    }

    private boolean isAvailable(List<ClassroomAvailabilityResponse> availability, Classroom classroom) {
        return availability.stream()
                .filter(a -> a.id().equals(classroom.getId()))
                .findFirst()
                .orElseThrow()
                .available();
    }

    @Test
    void classroomUsedByAnActiveSectionOfTheTermIsReportedAsOccupied() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(section, classroom, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(term.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), null);

        assertThat(isAvailable(availability, classroom)).isFalse();
    }

    @Test
    void classroomUsedByAnApprovedCycleSectionIsReportedAsOccupied() {
        Classroom classroom = fixtures.classroom(20);
        Term term = fixtures.term(2027);
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        fixtures.slot(section, classroom, DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(term.getId(), DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), null);

        assertThat(isAvailable(availability, classroom)).isFalse();
    }

    @Test
    void classroomUsedOnlyInAnotherYearIsReportedAsFree() {
        Classroom classroom = fixtures.classroom(30);
        SubjectSection pastSection = fixtures.section(fixtures.subject(), fixtures.term(2026), StatusType.ACTIVE, null);
        fixtures.slot(pastSection, classroom, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        Term nextYear = fixtures.term(2027);
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(nextYear.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), null);

        assertThat(isAvailable(availability, classroom)).isTrue();
    }

    @Test
    void classroomOccupiedInAnEarlierTermWithoutDateOverlapIsAvailableAndInformative() {
        Classroom classroom = fixtures.classroom(30);
        SubjectSection pastSection = fixtures.section(fixtures.subject(), fixtures.term(2025), StatusType.ACTIVE, null);
        fixtures.slot(pastSection, classroom, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        Term current = fixtures.term(2026);
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(current.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), null);

        var entry = entryOf(availability, classroom);
        assertThat(entry.available()).isTrue();
        assertThat(entry.overlapsInDates()).isFalse();
        assertThat(entry.occupant().section().id()).isEqualTo(pastSection.getId());
        assertThat(entry.occupant().section().kind()).isEqualTo(ClassroomSlotOccupancyResponse.SectionKind.PROGRAM);
        assertThat(entry.occupant().term().id()).isEqualTo(pastSection.getTerm().getId());
    }

    @Test
    void realConflictReportsTheOccupantWithOverlapAndPrefersItOverInformativeOnes() {
        Classroom classroom = fixtures.classroom(30);
        SubjectSection pastSection = fixtures.section(fixtures.subject(), fixtures.term(2025), StatusType.ACTIVE, null);
        fixtures.slot(pastSection, classroom, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        Term current = fixtures.term(2026);
        SubjectSection currentSection = fixtures.section(fixtures.subject(), current, StatusType.ACTIVE, null);
        fixtures.slot(currentSection, classroom, DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(21, 0));
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(current.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), null);

        var entry = entryOf(availability, classroom);
        assertThat(entry.available()).isFalse();
        assertThat(entry.overlapsInDates()).isTrue();
        assertThat(entry.occupant().section().id()).isEqualTo(currentSection.getId());
        assertThat(entry.occupant().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(entry.occupant().startTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void freeClassroomHasNoOccupantNorOverlapFlag() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        fixtures.flushAndClear();

        var entry = entryOf(getAvailability.getAvailability(term.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), null), classroom);

        assertThat(entry.available()).isTrue();
        assertThat(entry.occupant()).isNull();
        assertThat(entry.overlapsInDates()).isNull();
    }

    @Test
    void occupantOutsideTheRequestedHoursIsNotReported() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2026);
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(section, classroom, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        fixtures.flushAndClear();

        var entry = entryOf(getAvailability.getAvailability(term.getId(), DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(16, 0), null), classroom);

        assertThat(entry.available()).isTrue();
        assertThat(entry.occupant()).isNull();
    }

    @Test
    void smallClassroomReportsCapacityShortfallWhenSeatsAreProvided() {
        Classroom small = fixtures.classroom(20);
        Classroom exact = fixtures.classroom(35);
        Term term = fixtures.term(2026);
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(term.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), 35);

        assertThat(entryOf(availability, small).capacityShortfall()).isEqualTo(15);
        assertThat(entryOf(availability, exact).capacityShortfall()).isNull();
    }

    @Test
    void capacityShortfallIsAbsentWithoutSeats() {
        Classroom small = fixtures.classroom(20);
        Term term = fixtures.term(2026);
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(term.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), null);

        assertThat(entryOf(availability, small).capacityShortfall()).isNull();
    }

    @Test
    void availabilityQueryCountIsConstantRegardlessOfOccupiedClassrooms() {
        Term term = fixtures.term(2026);
        for (int i = 0; i < 6; i++) {
            Classroom classroom = fixtures.classroom(30);
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
            fixtures.slot(section, classroom, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        }
        fixtures.flushAndClear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        getAvailability.getAvailability(term.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), null);

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    private ClassroomAvailabilityResponse entryOf(List<ClassroomAvailabilityResponse> availability, Classroom classroom) {
        return availability.stream().filter(a -> a.id().equals(classroom.getId())).findFirst().orElseThrow();
    }
}
