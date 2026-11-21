package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionModificationConflictType;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SlotOutsideShiftException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionModificationConflictDetectorTest {

    private static final Long CLASSROOM_ID = 3L;

    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private SectionConflictChecker conflictChecker;
    @Mock
    private SectionShiftSlotGuard shiftSlotGuard;

    private SubjectSection section;
    private SectionModificationConflictDetector detector;

    @BeforeEach
    void setUp() {
        var term = TermFixtures.newTerm();
        term.setId(11L);
        section = new SubjectSection();
        section.setId(7L);
        section.setCapacity(30);
        section.setTerm(term);
        detector = new SectionModificationConflictDetector(classroomRepository, conflictChecker, shiftSlotGuard);
    }

    private Classroom classroom(int capacity) {
        var classroom = new Classroom();
        classroom.setId(CLASSROOM_ID);
        classroom.setCapacity(capacity);
        return classroom;
    }

    private ScheduleSlotRequest slot(int startHour, int endHour) {
        return new ScheduleSlotRequest(CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private SectionScheduleSlot occupyingSlot() {
        var subject = new Subject();
        subject.setName("Algebra");
        var other = new SubjectSection();
        other.setName("Comisión B");
        other.setSubject(subject);
        var occupying = new SectionScheduleSlot();
        occupying.setId(50L);
        occupying.setSubjectSection(other);
        return occupying;
    }

    @Test
    void adjacentSlotsInTheSameClassroomDoNotOverlap() {
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        when(shiftSlotGuard.findViolation(any(), any(), any())).thenReturn(Optional.empty());

        var conflicts = detector.detectAll(section, List.of(slot(18, 20), slot(20, 22)), Set.of());

        assertThat(conflicts).isEmpty();
    }

    @Test
    void slotsSharingAnyMinuteInTheSameClassroomOverlap() {
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        when(shiftSlotGuard.findViolation(any(), any(), any())).thenReturn(Optional.empty());

        var conflicts = detector.detectAll(section, List.of(slot(18, 20), slot(19, 21)), Set.of());

        assertThat(conflicts).singleElement().satisfies(conflict -> {
            assertThat(conflict.slotIndex()).isEqualTo(1);
            assertThat(conflict.type()).isEqualTo(SectionModificationConflictType.SLOTS_OVERLAP);
        });
    }

    @Test
    void classroomExactlyAsBigAsTheSectionCapacityIsNotAConflictButOneSeatLessIs() {
        when(shiftSlotGuard.findViolation(any(), any(), any())).thenReturn(Optional.empty());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        assertThat(detector.detectAll(section, List.of(slot(18, 20)), Set.of())).isEmpty();

        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(29)));
        assertThat(detector.detectAll(section, List.of(slot(18, 20)), Set.of())).singleElement()
                .extracting(SectionModificationConflict::type)
                .isEqualTo(SectionModificationConflictType.CLASSROOM_CAPACITY_EXCEEDED);
    }

    @Test
    void occupiedClassroomReportsTheLabelOfTheOccupyingSection() {
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        when(shiftSlotGuard.findViolation(any(), any(), any())).thenReturn(Optional.empty());
        when(conflictChecker.findClassroomOccupyingSlotsIgnoring(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(occupyingSlot()));

        var conflicts = detector.detectAll(section, List.of(slot(18, 20)), Set.of());

        assertThat(conflicts).singleElement().satisfies(conflict -> {
            assertThat(conflict.type()).isEqualTo(SectionModificationConflictType.CLASSROOM_OCCUPIED);
            assertThat(conflict.conflictingSectionLabel()).isEqualTo("Algebra · Comisión B");
        });
    }

    @Test
    void busyInstructorIsReportedWithTheOccupyingSectionLabel() {
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        when(shiftSlotGuard.findViolation(any(), any(), any())).thenReturn(Optional.empty());
        when(conflictChecker.findSectionInstructorsOccupations(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SectionConflictChecker.InstructorOccupation(9L, occupyingSlot())));

        var conflicts = detector.detectAll(section, List.of(slot(18, 20)), Set.of());

        assertThat(conflicts).singleElement().satisfies(conflict -> {
            assertThat(conflict.type()).isEqualTo(SectionModificationConflictType.INSTRUCTOR_BUSY);
            assertThat(conflict.conflictingSectionLabel()).isEqualTo("Algebra · Comisión B");
        });
    }

    @Test
    void slotOutsideTheShiftIsReported() {
        section.setShift(SectionShift.MORNING);
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(30)));
        when(shiftSlotGuard.findViolation(any(), any(), any())).thenReturn(Optional.of(
                new SlotOutsideShiftException(SectionShift.MORNING, LocalTime.of(18, 0), LocalTime.of(20, 0), LocalTime.of(8, 0), LocalTime.of(12, 30))));

        var conflicts = detector.detectAll(section, List.of(slot(18, 20)), Set.of());

        assertThat(conflicts).singleElement()
                .extracting(SectionModificationConflict::type)
                .isEqualTo(SectionModificationConflictType.SLOT_OUTSIDE_SHIFT);
        assertThat(conflicts.get(0).exception().get()).isInstanceOf(SlotOutsideShiftException.class);
    }

    @Test
    void unknownClassroomFailsInsteadOfBeingReportedAsAConflict() {
        when(shiftSlotGuard.findViolation(any(), any(), any())).thenReturn(Optional.empty());
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> detector.detectAll(section, List.of(slot(18, 20)), Set.of()))
                .isInstanceOf(ClassroomNotFoundException.class);
    }
}
