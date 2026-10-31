package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.ProposedSlot;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionModificationStatus;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.academicstructure.exception.SectionModificationAlreadyPendingException;
import com.red_escuela.backend.academicstructure.exception.SectionModificationNotPendingException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionModificationSpecialistsTest {

    private static final Long SECTION_ID = 7L;
    private static final Long CLASSROOM_ID = 3L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);

    @Mock
    private SubjectSectionRepository sectionRepository;
    @Mock
    private SectionScheduleSlotRepository slotRepository;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private SectionModificationProposalRepository proposalRepository;
    @Mock
    private SectionConflictChecker conflictChecker;

    private SubjectSection section;
    private Term term;

    @BeforeEach
    void setUp() {
        term = new Term();
        term.setId(11L);
        section = new SubjectSection();
        section.setId(SECTION_ID);
        section.setCapacity(30);
        section.setTerm(term);
    }

    private SectionModificationRequester requester() {
        return new SectionModificationRequester(sectionRepository, slotRepository, classroomRepository, proposalRepository, conflictChecker, CLOCK);
    }

    private SectionModificationApprover approver() {
        return new SectionModificationApprover(sectionRepository, slotRepository, proposalRepository, conflictChecker, CLOCK);
    }

    private SectionModificationRejecter rejecter() {
        return new SectionModificationRejecter(sectionRepository, proposalRepository, CLOCK);
    }

    private Classroom classroom(int capacity) {
        var classroom = new Classroom();
        classroom.setId(CLASSROOM_ID);
        classroom.setCapacity(capacity);
        return classroom;
    }

    private ScheduleSlotRequest slotRequest(LocalTime start, LocalTime end) {
        return new ScheduleSlotRequest(CLASSROOM_ID, DayOfWeek.MONDAY, start, end);
    }

    private SectionModificationProposal pendingProposal(boolean replacesSchedule, List<ProposedSlot> slots) {
        return SectionModificationProposal.pending(section, replacesSchedule, slots, NOW);
    }

    @Test
    void requestStoresTheProposalAndNeverTouchesTheLiveSlots() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));

        requester().requestModification(SECTION_ID, new ModifySectionRequest(List.of(slotRequest(LocalTime.of(18, 0), LocalTime.of(22, 0)))));

        var saved = ArgumentCaptor.forClass(SectionModificationProposal.class);
        verify(proposalRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SectionModificationStatus.PENDING);
        assertThat(saved.getValue().isReplacesSchedule()).isTrue();
        assertThat(saved.getValue().getRequestedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(slot.getEndTime()).isEqualTo(LocalTime.of(22, 0));
        });
        assertThat(section.isPendingApproval()).isTrue();
        verify(slotRepository, never()).deleteAll(any());
        verify(slotRepository, never()).save(any());
    }

    @Test
    void requestWithoutSlotsRecordsAProposalThatKeepsTheSchedule() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));

        requester().requestModification(SECTION_ID, new ModifySectionRequest(null));

        var saved = ArgumentCaptor.forClass(SectionModificationProposal.class);
        verify(proposalRepository).save(saved.capture());
        assertThat(saved.getValue().isReplacesSchedule()).isFalse();
        assertThat(section.isPendingApproval()).isTrue();
        verifyNoInteractions(slotRepository, classroomRepository, conflictChecker);
    }

    @Test
    void requestChecksAvailabilityIgnoringTheSectionsOwnCurrentSlots() {
        var current = new SectionScheduleSlot();
        current.setId(99L);
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of(current));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));

        requester().requestModification(SECTION_ID, new ModifySectionRequest(List.of(slotRequest(LocalTime.of(18, 0), LocalTime.of(22, 0)))));

        verify(conflictChecker).checkClassroomAvailabilityIgnoringSlots(
                term, CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), Set.of(99L));
    }

    @Test
    void requestWhileAnotherIsPendingFailsWithConflictAndStoresNothing() {
        section.setPendingApproval(true);
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> requester().requestModification(SECTION_ID, new ModifySectionRequest(null)))
                .isInstanceOf(SectionModificationAlreadyPendingException.class);

        verify(proposalRepository, never()).save(any());
    }

    @Test
    void requestWithOverlappingProposedSlotsInTheSameClassroomIsRejected() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        var request = new ModifySectionRequest(List.of(
                slotRequest(LocalTime.of(18, 0), LocalTime.of(20, 0)),
                slotRequest(LocalTime.of(19, 0), LocalTime.of(21, 0))));

        assertThatThrownBy(() -> requester().requestModification(SECTION_ID, request))
                .isInstanceOf(SectionScheduleConflictException.class);

        verify(proposalRepository, never()).save(any());
    }

    @Test
    void requestForUnknownSectionClassroomOrTooSmallClassroomFailsAndStoresNothing() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> requester().requestModification(SECTION_ID, new ModifySectionRequest(null)))
                .isInstanceOf(SectionNotFoundException.class);

        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        var request = new ModifySectionRequest(List.of(slotRequest(LocalTime.of(18, 0), LocalTime.of(22, 0))));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> requester().requestModification(SECTION_ID, request))
                .isInstanceOf(ClassroomNotFoundException.class);

        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(29)));
        assertThatThrownBy(() -> requester().requestModification(SECTION_ID, request))
                .isInstanceOf(SectionCapacityExceedsClassroomException.class);

        verify(proposalRepository, never()).save(any());
        assertThat(section.isPendingApproval()).isFalse();
    }

    @Test
    void requestWithClassroomOccupiedByAnotherSectionPropagatesTheConflict() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        doThrow(new SectionScheduleConflictException(CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0)))
                .when(conflictChecker).checkClassroomAvailabilityIgnoringSlots(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> requester().requestModification(SECTION_ID, new ModifySectionRequest(List.of(slotRequest(LocalTime.of(18, 0), LocalTime.of(22, 0))))))
                .isInstanceOf(SectionScheduleConflictException.class);

        verify(proposalRepository, never()).save(any());
    }

    @Test
    void approvingWithoutAPendingProposalFailsWithConflict() {
        section.setPendingApproval(true);
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(proposalRepository.findFirstBySubjectSectionIdAndStatus(SECTION_ID, SectionModificationStatus.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approver().approveModification(SECTION_ID))
                .isInstanceOf(SectionModificationNotPendingException.class);

        assertThat(section.isPendingApproval()).isTrue();
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void approvingAProposalThatKeepsTheScheduleOnlyClearsThePendingFlag() {
        section.setPendingApproval(true);
        var proposal = pendingProposal(false, List.of());
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(proposalRepository.findFirstBySubjectSectionIdAndStatus(SECTION_ID, SectionModificationStatus.PENDING)).thenReturn(Optional.of(proposal));

        approver().approveModification(SECTION_ID);

        assertThat(proposal.getStatus()).isEqualTo(SectionModificationStatus.APPROVED);
        assertThat(proposal.getResolvedAt()).isEqualTo(NOW);
        assertThat(section.isPendingApproval()).isFalse();
        verify(slotRepository, never()).deleteAll(any());
    }

    @Test
    void approvingReplacesTheLiveSlotsWithTheProposedOnes() {
        section.setPendingApproval(true);
        var classroom = classroom(30);
        var proposal = pendingProposal(true, List.of(new ProposedSlot(classroom, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(12, 0))));
        var oldSlot = new SectionScheduleSlot();
        oldSlot.setId(5L);
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(proposalRepository.findFirstBySubjectSectionIdAndStatus(SECTION_ID, SectionModificationStatus.PENDING)).thenReturn(Optional.of(proposal));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of(oldSlot));

        approver().approveModification(SECTION_ID);

        verify(conflictChecker).checkClassroomAvailabilityIgnoringSlots(term, CLASSROOM_ID, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(12, 0), Set.of(5L));
        verify(slotRepository).deleteAll(List.of(oldSlot));
        var saved = ArgumentCaptor.forClass(SectionScheduleSlot.class);
        verify(slotRepository).save(saved.capture());
        assertThat(saved.getValue().getSubjectSection()).isSameAs(section);
        assertThat(saved.getValue().getClassroom()).isSameAs(classroom);
        assertThat(saved.getValue().getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
    }

    @Test
    void approvingFailsWhenTheClassroomWasTakenAfterTheRequest() {
        section.setPendingApproval(true);
        var proposal = pendingProposal(true, List.of(new ProposedSlot(classroom(30), DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(12, 0))));
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(proposalRepository.findFirstBySubjectSectionIdAndStatus(SECTION_ID, SectionModificationStatus.PENDING)).thenReturn(Optional.of(proposal));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        doThrow(new SectionScheduleConflictException(CLASSROOM_ID, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)))
                .when(conflictChecker).checkClassroomAvailabilityIgnoringSlots(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> approver().approveModification(SECTION_ID))
                .isInstanceOf(SectionScheduleConflictException.class);

        verify(slotRepository, never()).deleteAll(any());
        assertThat(proposal.isPending()).isTrue();
    }

    @Test
    void rejectingStoresTheReasonKeepsTheScheduleAndClearsThePendingFlag() {
        section.setPendingApproval(true);
        var proposal = pendingProposal(true, List.of());
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(proposalRepository.findFirstBySubjectSectionIdAndStatus(SECTION_ID, SectionModificationStatus.PENDING)).thenReturn(Optional.of(proposal));

        rejecter().rejectModification(SECTION_ID, "classroom reserved for exams");

        assertThat(proposal.getStatus()).isEqualTo(SectionModificationStatus.REJECTED);
        assertThat(proposal.getRejectionReason()).isEqualTo("classroom reserved for exams");
        assertThat(proposal.getResolvedAt()).isEqualTo(NOW);
        assertThat(section.isPendingApproval()).isFalse();
        verifyNoInteractions(slotRepository);
    }

    @Test
    void rejectingWithoutAPendingProposalOrForAnUnknownSectionFails() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(proposalRepository.findFirstBySubjectSectionIdAndStatus(SECTION_ID, SectionModificationStatus.PENDING)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> rejecter().rejectModification(SECTION_ID, "x"))
                .isInstanceOf(SectionModificationNotPendingException.class);

        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> rejecter().rejectModification(SECTION_ID, "x"))
                .isInstanceOf(SectionNotFoundException.class);

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void resolvingAnAlreadyResolvedProposalIsAnIllegalState() {
        var proposal = pendingProposal(false, List.of());
        proposal.approve(NOW);

        assertThatThrownBy(() -> proposal.reject("late", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> proposal.approve(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
