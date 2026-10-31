package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.ClassroomInUseException;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomDeactivatorTest {

    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private SectionScheduleSlotRepository slotRepository;
    @Mock
    private SectionModificationProposalRepository proposalRepository;

    private Classroom classroom;

    @BeforeEach
    void setUp() {
        classroom = new Classroom();
        classroom.setId(5L);
        classroom.setStatus(StatusType.ACTIVE);
        org.mockito.Mockito.lenient().when(classroomRepository.findById(5L)).thenReturn(Optional.of(classroom));
    }

    private ClassroomDeactivator deactivator() {
        return new ClassroomDeactivator(classroomRepository, slotRepository, proposalRepository);
    }

    @Test
    void deactivatingAnUnusedClassroomMarksItInactiveAndPersistsIt() {
        when(slotRepository.findSlotsOfActiveSectionsInClassroom(5L)).thenReturn(List.of());
        when(proposalRepository.findSectionNamesWithPendingProposalIn(5L)).thenReturn(List.of());

        deactivator().deactivateClassroom(5L);

        assertThat(classroom.getStatus()).isEqualTo(StatusType.INACTIVE);
        verify(classroomRepository).save(classroom);
    }

    @Test
    void deactivatingAClassroomUsedBySectionsFailsListingEveryAffectedSectionAndKeepsItActive() {
        var subjectSection = new SubjectSection();
        subjectSection.setName("Comision B");
        var courseSection = new CourseSubjectSection();
        courseSection.setName("Comision A");
        var first = new SectionScheduleSlot();
        first.setSubjectSection(subjectSection);
        var second = new SectionScheduleSlot();
        second.setCourseSection(courseSection);
        when(slotRepository.findSlotsOfActiveSectionsInClassroom(5L)).thenReturn(List.of(first, second));
        when(proposalRepository.findSectionNamesWithPendingProposalIn(5L)).thenReturn(List.of("Comision C"));

        assertThatThrownBy(() -> deactivator().deactivateClassroom(5L))
                .isInstanceOf(ClassroomInUseException.class)
                .hasMessageContaining("Comision A, Comision B, Comision C");

        assertThat(classroom.getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    void deactivatingAnUnknownClassroomFailsWithClassroomNotFound() {
        when(classroomRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deactivator().deactivateClassroom(9L)).isInstanceOf(ClassroomNotFoundException.class);

        verify(classroomRepository, never()).save(any());
    }
}
