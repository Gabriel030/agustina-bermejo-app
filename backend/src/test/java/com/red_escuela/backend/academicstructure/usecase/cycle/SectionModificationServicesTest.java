package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.service.SectionConflictChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionModificationServicesTest {

    private static final Long SECTION_ID = 7L;
    private static final Long CLASSROOM_ID = 3L;

    @Mock
    private SubjectSectionRepository sectionRepository;
    @Mock
    private SectionScheduleSlotRepository slotRepository;
    @Mock
    private ClassroomRepository classroomRepository;
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

    private RequestSectionModificationService requestService() {
        return new RequestSectionModificationService(sectionRepository, slotRepository, classroomRepository, conflictChecker);
    }

    private Classroom classroom(int capacity) {
        var classroom = new Classroom();
        classroom.setId(CLASSROOM_ID);
        classroom.setCapacity(capacity);
        return classroom;
    }

    private ScheduleSlotRequest slotRequest() {
        return new ScheduleSlotRequest(CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
    }

    @Test
    void requestWithNewSlotsReplacesTheExistingSlotsAndMarksTheSectionPending() {
        var oldSlot = new SectionScheduleSlot();
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of(oldSlot));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));

        requestService().requestModification(SECTION_ID, new ModifySectionRequest(List.of(slotRequest())));

        verify(slotRepository).deleteAll(List.of(oldSlot));
        verify(conflictChecker).checkClassroomAvailability(term, CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        var saved = org.mockito.ArgumentCaptor.forClass(SectionScheduleSlot.class);
        verify(slotRepository).save(saved.capture());
        assertThat(saved.getValue().getSubjectSection()).isSameAs(section);
        assertThat(saved.getValue().getClassroom().getId()).isEqualTo(CLASSROOM_ID);
        assertThat(saved.getValue().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(saved.getValue().getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(saved.getValue().getEndTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(section.isPendingApproval()).isTrue();
        verify(sectionRepository).save(section);
    }

    @Test
    void requestWithoutSlotsOnlyMarksTheSectionPendingAndKeepsItsSchedule() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));

        requestService().requestModification(SECTION_ID, new ModifySectionRequest(null));

        assertThat(section.isPendingApproval()).isTrue();
        verify(sectionRepository).save(section);
        verifyNoInteractions(slotRepository, classroomRepository, conflictChecker);
    }

    @Test
    void requestWithEmptySlotListClearsTheScheduleWithoutCreatingSlots() {
        var oldSlot = new SectionScheduleSlot();
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of(oldSlot));

        requestService().requestModification(SECTION_ID, new ModifySectionRequest(List.of()));

        verify(slotRepository).deleteAll(List.of(oldSlot));
        verify(slotRepository, never()).save(any());
        assertThat(section.isPendingApproval()).isTrue();
    }

    @Test
    void requestForUnknownSectionFailsWithSectionNotFound() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService().requestModification(SECTION_ID, new ModifySectionRequest(null)))
                .isInstanceOf(SectionNotFoundException.class);

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void requestWithUnknownClassroomFailsAndDoesNotMarkThePendingFlag() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService().requestModification(SECTION_ID, new ModifySectionRequest(List.of(slotRequest()))))
                .isInstanceOf(ClassroomNotFoundException.class);

        assertThat(section.isPendingApproval()).isFalse();
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void requestWithClassroomSmallerThanTheSectionCapacityIsRejected() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(29)));

        assertThatThrownBy(() -> requestService().requestModification(SECTION_ID, new ModifySectionRequest(List.of(slotRequest()))))
                .isInstanceOf(SectionCapacityExceedsClassroomException.class);

        verify(slotRepository, never()).save(any());
        assertThat(section.isPendingApproval()).isFalse();
    }

    @Test
    void requestWithClassroomOccupiedAtThatTimePropagatesTheConflictAndSavesNothing() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
        when(slotRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        doThrow(new IllegalStateException("occupied")).when(conflictChecker)
                .checkClassroomAvailability(term, CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));

        assertThatThrownBy(() -> requestService().requestModification(SECTION_ID, new ModifySectionRequest(List.of(slotRequest()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("occupied");

        verify(slotRepository, never()).save(any());
        assertThat(section.isPendingApproval()).isFalse();
    }

    @Test
    void approvingAPendingModificationClearsThePendingFlag() {
        section.setPendingApproval(true);
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));

        new ApproveSectionModificationService(sectionRepository).approveModification(SECTION_ID);

        assertThat(section.isPendingApproval()).isFalse();
        verify(sectionRepository).save(section);
    }

    @Test
    void approvingAModificationOfAnUnknownSectionFailsWithSectionNotFound() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ApproveSectionModificationService(sectionRepository).approveModification(SECTION_ID))
                .isInstanceOf(SectionNotFoundException.class);

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void rejectingAPendingModificationClearsThePendingFlag() {
        section.setPendingApproval(true);
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));

        new RejectSectionModificationService(sectionRepository).rejectModification(SECTION_ID, "conflicts with another course");

        assertThat(section.isPendingApproval()).isFalse();
        verify(sectionRepository).save(section);
    }

    @Test
    void rejectingAModificationOfAnUnknownSectionFailsWithSectionNotFound() {
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RejectSectionModificationService(sectionRepository).rejectModification(SECTION_ID, "x"))
                .isInstanceOf(SectionNotFoundException.class);

        verify(sectionRepository, never()).save(any());
    }
}
