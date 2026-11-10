package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CycleApprovalSupportIT extends AbstractIntegrationTest {

    private static final LocalTime SEVEN_PM = LocalTime.of(19, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);

    @Autowired
    private CycleConflictFinder conflictFinder;
    @Autowired
    private AcademicOfferingCycleReader cycleReader;
    @Autowired
    private AcademicOfferingCycleRepository cycleRepository;
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

    @Test
    void aCycleWithoutOverlapsHasNoConflicts() {
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var section = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.slot(section, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.assignLead(section, teacher);
        fixtures.flushAndClear();

        var result = conflictFinder.find(cycle.getId());

        assertThat(result.hasConflicts()).isFalse();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    void aClassroomTakenByAnActiveCommissionIsReportedWithBothSides() {
        var active = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(active, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var pending = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.slot(pending, roomA, DayOfWeek.MONDAY, LocalTime.of(20, 0), LocalTime.of(22, 0));
        fixtures.flushAndClear();

        var result = conflictFinder.find(cycle.getId());

        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.conflicts()).hasSize(1);
        var conflict = result.conflicts().get(0);
        assertThat(conflict.type()).isEqualTo("CLASSROOM");
        assertThat(conflict.classroomId()).isEqualTo(roomA.getId());
        assertThat(conflict.dayOfWeek()).isEqualTo("MONDAY");
        assertThat(conflict.instructorId()).isNull();
        assertThat(conflict.ownSection().sectionId()).isEqualTo(pending.getId());
        assertThat(conflict.ownSection().cycleId()).isEqualTo(cycle.getId());
        assertThat(conflict.otherSection().sectionId()).isEqualTo(active.getId());
        assertThat(conflict.otherSection().cycleId()).isNull();
    }

    @Test
    void aTeacherTeachingAtTheSameTimeIsReportedAsAnInstructorConflict() {
        var active = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(active, roomA, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.assignLead(active, teacher);
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var pending = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.slot(pending, roomB, DayOfWeek.MONDAY, SEVEN_PM, NINE_PM);
        fixtures.assignLead(pending, teacher);
        fixtures.flushAndClear();

        var result = conflictFinder.find(cycle.getId());

        assertThat(result.conflicts()).hasSize(1);
        var conflict = result.conflicts().get(0);
        assertThat(conflict.type()).isEqualTo("INSTRUCTOR");
        assertThat(conflict.instructorId()).isEqualTo(teacher.getId());
        assertThat(conflict.instructorName()).isNotBlank();
        assertThat(conflict.classroomId()).isEqualTo(roomB.getId());
        assertThat(conflict.otherSection().sectionId()).isEqualTo(active.getId());
    }

    @Test
    void anOverlapBetweenTwoSectionsOfTheSameCycleIsReportedOnce() {
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var first = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        var second = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.slot(first, roomA, DayOfWeek.TUESDAY, SEVEN_PM, NINE_PM);
        fixtures.slot(second, roomA, DayOfWeek.TUESDAY, SEVEN_PM, NINE_PM);
        fixtures.flushAndClear();

        var result = conflictFinder.find(cycle.getId());

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).otherSection().cycleId()).isEqualTo(cycle.getId());
    }

    @Test
    void conflictsOfAnUnknownCycleFailWithCycleNotFound() {
        assertThatThrownBy(() -> conflictFinder.find(-1L)).isInstanceOf(CycleNotFoundException.class);
    }

    @Test
    void theCycleDetailExposesTheSubmissionAndTheSectionCount() {
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var submitter = fixtures.person();
        cycle.setSubmittedBy(submitter);
        cycle.setSubmittedAt(LocalDateTime.of(2026, 9, 2, 11, 0));
        fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.flushAndClear();

        var detail = cycleReader.readCycle(cycle.getId());

        assertThat(detail.sectionCount()).isEqualTo(2L);
        assertThat(detail.submittedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 11, 0));
        assertThat(detail.submittedByName()).isEqualTo(submitter.getName() + " " + submitter.getLastname());
    }

    @Test
    void theCycleListCountsSectionsPerCycleWithoutMixingThem() {
        var withTwo = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var empty = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, withTwo);
        fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, withTwo);
        fixtures.flushAndClear();

        var list = cycleReader.listCycles(null, null, AcademicOfferingCycleStatus.PENDING_APPROVAL);

        assertThat(list).filteredOn(c -> c.id().equals(withTwo.getId())).singleElement().satisfies(c -> {
            assertThat(c.sectionCount()).isEqualTo(2L);
            assertThat(c.submittedByName()).isNull();
        });
        assertThat(list).filteredOn(c -> c.id().equals(empty.getId())).singleElement()
                .satisfies(c -> assertThat(c.sectionCount()).isZero());
    }

    @Test
    void theLockedLookupFindsTheCycle() {
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        fixtures.flushAndClear();

        assertThat(cycleRepository.findByIdForUpdate(cycle.getId())).isPresent();
    }
}
