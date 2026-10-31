package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleReader;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.usecase.classroom.DeactivateClassroomUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.ConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SectionModificationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private RequestSectionModificationUseCase requestModification;
    @Autowired
    private ApproveSectionModificationUseCase approveModification;
    @Autowired
    private RejectSectionModificationUseCase rejectModification;
    @Autowired
    private DeactivateClassroomUseCase deactivateClassroom;
    @Autowired
    private SectionScheduleSlotRepository slotRepository;
    @Autowired
    private SubjectSectionRepository sectionRepository;
    @Autowired
    private AcademicOfferingCycleReader cycleReader;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long cycleId;
    private Long sectionId;
    private Long currentClassroomId;
    private Long proposedClassroomId;

    @BeforeEach
    void seedSectionWithOneMondaySlot() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            var term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
            var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            Classroom current = fixtures.classroom(30);
            Classroom proposed = fixtures.classroom(30);
            fixtures.slot(section, current, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));
            cycleId = cycle.getId();
            sectionId = section.getId();
            currentClassroomId = current.getId();
            proposedClassroomId = proposed.getId();
        });
    }

    private ModifySectionRequest tuesdayInProposedClassroom() {
        return new ModifySectionRequest(List.of(
                new ScheduleSlotRequest(proposedClassroomId, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(12, 0))));
    }

    private List<SectionScheduleSlot> liveSlots() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var slots = slotRepository.findBySubjectSectionId(sectionId);
            slots.forEach(slot -> slot.getClassroom().getId());
            return slots;
        });
    }

    @Test
    void requestingAModificationLeavesTheLiveScheduleUntouchedUntilApproval() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());

        assertThat(liveSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(slot.getClassroom().getId()).isEqualTo(currentClassroomId);
        });
    }

    @Test
    void rejectingAModificationKeepsTheOriginalSchedule() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());

        rejectModification.rejectModification(sectionId, "classroom reserved for exams");

        assertThat(liveSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(slot.getClassroom().getId()).isEqualTo(currentClassroomId);
        });
        assertThat(sectionRepository.findById(sectionId)).get().extracting(SubjectSection::isPendingApproval).isEqualTo(false);
    }

    @Test
    void approvingAModificationReplacesTheScheduleWithTheProposedSlots() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());

        approveModification.approveModification(sectionId);

        assertThat(liveSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(slot.getClassroom().getId()).isEqualTo(proposedClassroomId);
        });
    }

    @Test
    void requestingTwiceWhileAModificationIsPendingIsAConflict() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());

        assertThatThrownBy(() -> requestModification.requestModification(sectionId, tuesdayInProposedClassroom()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approvingASectionWithoutAPendingModificationIsAConflict() {
        assertThatThrownBy(() -> approveModification.approveModification(sectionId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectingASectionWithoutAPendingModificationIsAConflict() {
        assertThatThrownBy(() -> rejectModification.rejectModification(sectionId, "x"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approvingTwiceIsAConflictTheSecondTime() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());
        approveModification.approveModification(sectionId);

        assertThatThrownBy(() -> approveModification.approveModification(sectionId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deactivatingAClassroomUsedByAnActiveSectionIsAConflict() {
        assertThatThrownBy(() -> deactivateClassroom.deactivate(currentClassroomId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theSectionReadShowsThePendingProposalToDirectionAndThenTheRejectionReason() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());

        var pending = cycleReader.listCycleSections(cycleId).get(0);
        assertThat(pending.pendingApproval()).isTrue();
        assertThat(pending.slots()).singleElement().extracting("dayOfWeek").isEqualTo("MONDAY");
        assertThat(pending.pendingModification().slots()).singleElement().satisfies(slot -> {
            assertThat(slot.dayOfWeek()).isEqualTo("TUESDAY");
            assertThat(slot.classroomId()).isEqualTo(proposedClassroomId);
        });
        assertThat(pending.lastRejectionReason()).isNull();

        rejectModification.rejectModification(sectionId, "classroom reserved for exams");

        var rejected = cycleReader.listCycleSections(cycleId).get(0);
        assertThat(rejected.pendingApproval()).isFalse();
        assertThat(rejected.pendingModification()).isNull();
        assertThat(rejected.lastRejectionReason()).isEqualTo("classroom reserved for exams");
    }

    @Test
    void aNewRequestAfterARejectionIsAcceptedAndHidesTheOldReason() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());
        rejectModification.rejectModification(sectionId, "classroom reserved for exams");

        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());

        var section = cycleReader.listCycleSections(cycleId).get(0);
        assertThat(section.pendingModification()).isNotNull();
        assertThat(section.lastRejectionReason()).isNull();
    }

    @Test
    void deactivatingAClassroomWithAPendingProposalForItIsAConflictAndAnUnusedOneIsDeactivated() {
        requestModification.requestModification(sectionId, tuesdayInProposedClassroom());

        assertThatThrownBy(() -> deactivateClassroom.deactivate(proposedClassroomId))
                .isInstanceOf(ConflictException.class);

        rejectModification.rejectModification(sectionId, "no");
        deactivateClassroom.deactivate(proposedClassroomId);
        assertThat(entityManager.find(Classroom.class, proposedClassroomId).getStatus()).isEqualTo(StatusType.INACTIVE);
    }
}
