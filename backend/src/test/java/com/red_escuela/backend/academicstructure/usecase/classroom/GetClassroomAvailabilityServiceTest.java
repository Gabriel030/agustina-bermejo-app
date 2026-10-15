package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
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

        var availability = getAvailability.getAvailability(term.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));

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

        var availability = getAvailability.getAvailability(term.getId(), DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThat(isAvailable(availability, classroom)).isFalse();
    }

    @Test
    void classroomUsedOnlyInAnotherYearIsReportedAsFree() {
        Classroom classroom = fixtures.classroom(30);
        SubjectSection pastSection = fixtures.section(fixtures.subject(), fixtures.term(2026), StatusType.ACTIVE, null);
        fixtures.slot(pastSection, classroom, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        Term nextYear = fixtures.term(2027);
        fixtures.flushAndClear();

        var availability = getAvailability.getAvailability(nextYear.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));

        assertThat(isAvailable(availability, classroom)).isTrue();
    }
}
