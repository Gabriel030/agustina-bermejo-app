package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CycleNotEditableException;
import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.StatusType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AcademicOfferingCycleUseCaseTest extends AbstractIntegrationTest {

    private static final LocalTime EVENING_START = LocalTime.of(18, 0);
    private static final LocalTime EVENING_END = LocalTime.of(20, 0);

    @Autowired
    private CreateCycleSectionUseCase createCycleSection;

    @Autowired
    private SubmitCycleForApprovalUseCase submitCycle;

    @Autowired
    private AcademicOfferingCycleRepository cycleRepository;

    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CycleTestFixtures(entityManager);
    }

    private CreateCycleSectionRequest eveningSectionRequest(Subject subject, Classroom classroom, int capacity) {
        return new CreateCycleSectionRequest(
                "A", subject.getId(), null, capacity,
                List.of(new ScheduleSlotRequest(classroom.getId(), DayOfWeek.MONDAY, EVENING_START, EVENING_END)));
    }

    private AcademicOfferingCycle cycleWithLeadSection(Term term, AcademicOfferingCycleStatus status) {
        Program program = fixtures.program();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, status);
        SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, cycle);
        fixtures.assignLead(section, fixtures.instructor());
        return cycle;
    }

    @Test
    void resubmittingAnApprovedCycleIsRejectedAndKeepsItApproved() {
        AcademicOfferingCycle cycle = cycleWithLeadSection(fixtures.term(2027), AcademicOfferingCycleStatus.APPROVED);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> submitCycle.submit(cycle.getId()))
                .isInstanceOf(CycleNotEditableException.class);
        assertThat(cycleRepository.findById(cycle.getId()).orElseThrow().getStatus())
                .isEqualTo(AcademicOfferingCycleStatus.APPROVED);
    }

    @Test
    void resubmittingAPendingCycleIsRejected() {
        AcademicOfferingCycle cycle = cycleWithLeadSection(fixtures.term(2027), AcademicOfferingCycleStatus.PENDING_APPROVAL);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> submitCycle.submit(cycle.getId()))
                .isInstanceOf(CycleNotEditableException.class);
    }

    @Test
    void addingASectionToAnApprovedCycleIsRejected() {
        Term term = fixtures.term(2027);
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        Subject subject = fixtures.subject();
        Classroom classroom = fixtures.classroom(30);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 20)))
                .isInstanceOf(CycleNotEditableException.class);
    }

    @Test
    void addingASectionToAPendingCycleIsRejected() {
        Term term = fixtures.term(2027);
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        Subject subject = fixtures.subject();
        Classroom classroom = fixtures.classroom(30);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 20)))
                .isInstanceOf(CycleNotEditableException.class);
    }

    @Test
    void rejectedCycleCanBeFixedAndResubmittedClearingTheRejection() {
        Term term = fixtures.term(2027);
        AcademicOfferingCycle cycle = cycleWithLeadSection(term, AcademicOfferingCycleStatus.REJECTED);
        cycle.setRejectionReason("Faltan comisiones de primer año");
        cycle.setDecidedBy(fixtures.person());
        Subject subject = fixtures.subject();
        Classroom classroom = fixtures.classroom(30);
        fixtures.flushAndClear();

        Long sectionId = createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 20));
        SubjectSection added = entityManager.find(SubjectSection.class, sectionId);
        fixtures.assignLead(added, fixtures.instructor());
        fixtures.flushAndClear();

        submitCycle.submit(cycle.getId());

        AcademicOfferingCycle resubmitted = cycleRepository.findById(cycle.getId()).orElseThrow();
        assertThat(resubmitted.getStatus()).isEqualTo(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        assertThat(resubmitted.getRejectionReason()).isNull();
        assertThat(resubmitted.getDecidedBy()).isNull();
    }

    @Test
    void sectionCapacityAboveClassroomCapacityIsRejected() {
        Term term = fixtures.term(2027);
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        Subject subject = fixtures.subject();
        Classroom classroom = fixtures.classroom(18);
        fixtures.flushAndClear();

        assertThatThrownBy(() -> createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 500)))
                .isInstanceOf(SectionCapacityExceedsClassroomException.class);
    }

    @Test
    void sectionCapacityEqualToClassroomCapacityIsAccepted() {
        Term term = fixtures.term(2027);
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        Subject subject = fixtures.subject();
        Classroom classroom = fixtures.classroom(18);
        fixtures.flushAndClear();

        assertThat(createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 18))).isNotNull();
    }

    @Test
    void classroomUsedOnlyInPreviousYearsIsFreeForANewYearCycle() {
        Classroom classroom = fixtures.classroom(30);
        for (int year : List.of(2024, 2025, 2026)) {
            Term pastTerm = fixtures.term(year);
            SubjectSection pastSection = fixtures.section(fixtures.subject(), pastTerm, StatusType.ACTIVE, null);
            fixtures.slot(pastSection, classroom, DayOfWeek.MONDAY, EVENING_START, EVENING_END);
        }
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), fixtures.term(2027), AcademicOfferingCycleStatus.DRAFT);
        Subject subject = fixtures.subject();
        fixtures.flushAndClear();

        assertThat(createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 20))).isNotNull();
    }

    @Test
    void classroomUsedByAnActiveSectionInTheSameTermStillConflicts() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2027);
        SubjectSection activeSection = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.slot(activeSection, classroom, DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(21, 0));
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        Subject subject = fixtures.subject();
        fixtures.flushAndClear();

        assertThatThrownBy(() -> createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 20)))
                .isInstanceOf(SectionScheduleConflictException.class);
    }

    @Test
    void classroomReservedByAnotherDraftCycleInTheSameTermConflicts() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2027);
        AcademicOfferingCycle otherDraft = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        SubjectSection draftSection = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, otherDraft);
        fixtures.slot(draftSection, classroom, DayOfWeek.MONDAY, EVENING_START, EVENING_END);
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        Subject subject = fixtures.subject();
        fixtures.flushAndClear();

        assertThatThrownBy(() -> createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 20)))
                .isInstanceOf(SectionScheduleConflictException.class);
    }

    @Test
    void sectionsOfARejectedCycleOrInactiveSectionsDoNotBlockTheClassroom() {
        Classroom classroom = fixtures.classroom(30);
        Term term = fixtures.term(2027);
        AcademicOfferingCycle rejected = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.REJECTED);
        SubjectSection rejectedSection = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, rejected);
        fixtures.slot(rejectedSection, classroom, DayOfWeek.MONDAY, EVENING_START, EVENING_END);
        SubjectSection deactivated = fixtures.section(fixtures.subject(), term, StatusType.INACTIVE, null);
        fixtures.slot(deactivated, classroom, DayOfWeek.MONDAY, EVENING_START, EVENING_END);
        AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        Subject subject = fixtures.subject();
        fixtures.flushAndClear();

        assertThat(createCycleSection.createSection(cycle.getId(), eveningSectionRequest(subject, classroom, 20))).isNotNull();
    }
}
