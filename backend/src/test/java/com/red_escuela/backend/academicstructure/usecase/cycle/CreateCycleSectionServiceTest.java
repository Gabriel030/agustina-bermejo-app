package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleNotEditableException;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotOpenableForSectionsException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.service.SectionConflictChecker;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCycleSectionServiceTest {

    private static final Long CYCLE_ID = 4L;
    private static final Long CLASSROOM_ID = 9L;

    @Mock
    private AcademicOfferingCycleRepository cycleRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private CourseSubjectRepository courseSubjectRepository;
    @Mock
    private SubjectSectionRepository subjectSectionRepository;
    @Mock
    private CourseSubjectSectionRepository courseSectionRepository;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private SectionScheduleSlotRepository slotRepository;
    @Mock
    private SectionConflictChecker conflictChecker;

    private CreateCycleSectionService service;
    private Term term;
    private AcademicOfferingCycle programCycle;
    private AcademicOfferingCycle courseCycle;

    @BeforeEach
    void setUp() {
        service = new CreateCycleSectionService(cycleRepository, subjectRepository, courseSubjectRepository,
                subjectSectionRepository, courseSectionRepository, classroomRepository, slotRepository, conflictChecker);
        term = new Term();
        term.setId(2L);
        programCycle = new AcademicOfferingCycle();
        programCycle.setTerm(term);
        programCycle.setProgram(Program.builder().id(1L).build());
        courseCycle = new AcademicOfferingCycle();
        courseCycle.setTerm(term);
    }

    private ScheduleSlotRequest slot(DayOfWeek day) {
        return new ScheduleSlotRequest(CLASSROOM_ID, day, LocalTime.of(8, 0), LocalTime.of(12, 0));
    }

    private Classroom classroom(int capacity) {
        var classroom = new Classroom();
        classroom.setId(CLASSROOM_ID);
        classroom.setCapacity(capacity);
        return classroom;
    }

    private Subject subject(SubjectStatus status) {
        var subject = new Subject();
        subject.setStatus(status);
        return subject;
    }

    private void persistSubjectSections() {
        when(subjectSectionRepository.save(any(SubjectSection.class))).thenAnswer(invocation -> {
            SubjectSection saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private void persistCourseSections() {
        when(courseSectionRepository.save(any(CourseSubjectSection.class))).thenAnswer(invocation -> {
            CourseSubjectSection saved = invocation.getArgument(0);
            saved.setId(200L);
            return saved;
        });
    }

    @Test
    void unknownCycleFailsWithCycleNotFound() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSection(CYCLE_ID, new CreateCycleSectionRequest("A", 1L, null, 10, null)))
                .isInstanceOf(CycleNotFoundException.class);

        verifyNoInteractions(subjectSectionRepository, courseSectionRepository);
    }

    @Test
    void pendingApprovalCycleDoesNotAcceptNewSections() {
        programCycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));

        assertThatThrownBy(() -> service.createSection(CYCLE_ID, new CreateCycleSectionRequest("A", 1L, null, 10, null)))
                .isInstanceOf(CycleNotEditableException.class);

        verifyNoInteractions(subjectRepository, subjectSectionRepository);
    }

    @Test
    void approvedCycleDoesNotAcceptNewSections() {
        courseCycle.setStatus(AcademicOfferingCycleStatus.APPROVED);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(courseCycle));

        assertThatThrownBy(() -> service.createSection(CYCLE_ID, new CreateCycleSectionRequest("A", null, 1L, 10, null)))
                .isInstanceOf(CycleNotEditableException.class);

        verifyNoInteractions(courseSubjectRepository, courseSectionRepository);
    }

    @Test
    void programCycleSectionIsCreatedInactiveInTheCycleTermWithoutSlots() {
        var subject = subject(SubjectStatus.ACTIVE);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        persistSubjectSections();

        var id = service.createSection(CYCLE_ID, new CreateCycleSectionRequest("Commission A", 1L, null, 25, null));

        assertThat(id).isEqualTo(100L);
        var captor = ArgumentCaptor.forClass(SubjectSection.class);
        verify(subjectSectionRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Commission A");
        assertThat(saved.getSubject()).isSameAs(subject);
        assertThat(saved.getTerm()).isSameAs(term);
        assertThat(saved.getCapacity()).isEqualTo(25);
        assertThat(saved.getStatus()).isEqualTo(StatusType.INACTIVE);
        assertThat(saved.getAcademicOfferingCycle()).isSameAs(programCycle);
        assertThat(saved.getCreatedAt()).isNotNull();
        verifyNoInteractions(slotRepository, courseSectionRepository);
    }

    @Test
    void programCycleSectionWithSlotsPersistsEachSlotLinkedToTheSection() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(SubjectStatus.ACTIVE)));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(25)));
        persistSubjectSections();

        service.createSection(CYCLE_ID, new CreateCycleSectionRequest("A", 1L, null, 25,
                List.of(slot(DayOfWeek.MONDAY), slot(DayOfWeek.TUESDAY))));

        verify(conflictChecker).checkClassroomAvailability(term, CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        verify(conflictChecker).checkClassroomAvailability(term, CLASSROOM_ID, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        var captor = ArgumentCaptor.forClass(SectionScheduleSlot.class);
        verify(slotRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SectionScheduleSlot::getDayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThat(captor.getAllValues()).allSatisfy(saved -> {
            assertThat(saved.getSubjectSection().getId()).isEqualTo(100L);
            assertThat(saved.getCourseSection()).isNull();
            assertThat(saved.getClassroom().getId()).isEqualTo(CLASSROOM_ID);
            assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(12, 0));
        });
    }

    @Test
    void programCycleWithUnknownSubjectFailsWithSubjectNotFound() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSection(CYCLE_ID, new CreateCycleSectionRequest("A", 1L, null, 10, null)))
                .isInstanceOf(SubjectNotFoundException.class);

        verifyNoInteractions(subjectSectionRepository);
    }

    @Test
    void programCycleWithDraftSubjectCannotOpenSections() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(SubjectStatus.DRAFT)));

        assertThatThrownBy(() -> service.createSection(CYCLE_ID, new CreateCycleSectionRequest("A", 1L, null, 10, null)))
                .isInstanceOf(SubjectNotOpenableForSectionsException.class);

        verifyNoInteractions(subjectSectionRepository);
    }

    @Test
    void programCycleSlotWithUnknownClassroomFailsWithClassroomNotFound() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(SubjectStatus.ACTIVE)));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.empty());
        persistSubjectSections();

        assertThatThrownBy(() -> service.createSection(CYCLE_ID,
                new CreateCycleSectionRequest("A", 1L, null, 10, List.of(slot(DayOfWeek.MONDAY)))))
                .isInstanceOf(ClassroomNotFoundException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void programCycleSlotInAClassroomTooSmallIsRejected() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(SubjectStatus.ACTIVE)));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(9)));
        persistSubjectSections();

        assertThatThrownBy(() -> service.createSection(CYCLE_ID,
                new CreateCycleSectionRequest("A", 1L, null, 10, List.of(slot(DayOfWeek.MONDAY)))))
                .isInstanceOf(SectionCapacityExceedsClassroomException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void programCycleSlotConflictPropagatesAndSavesNoSlot() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(SubjectStatus.ACTIVE)));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(10)));
        persistSubjectSections();
        doThrow(new IllegalStateException("occupied")).when(conflictChecker)
                .checkClassroomAvailability(term, CLASSROOM_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThatThrownBy(() -> service.createSection(CYCLE_ID,
                new CreateCycleSectionRequest("A", 1L, null, 10, List.of(slot(DayOfWeek.MONDAY)))))
                .isInstanceOf(IllegalStateException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void courseCycleSectionIsCreatedInactiveForTheCourseSubject() {
        var courseSubject = new CourseSubject();
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(courseCycle));
        when(courseSubjectRepository.findById(3L)).thenReturn(Optional.of(courseSubject));
        persistCourseSections();

        var id = service.createSection(CYCLE_ID, new CreateCycleSectionRequest("Commission B", null, 3L, 18, null));

        assertThat(id).isEqualTo(200L);
        var captor = ArgumentCaptor.forClass(CourseSubjectSection.class);
        verify(courseSectionRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Commission B");
        assertThat(saved.getCourseSubject()).isSameAs(courseSubject);
        assertThat(saved.getTerm()).isSameAs(term);
        assertThat(saved.getCapacity()).isEqualTo(18);
        assertThat(saved.getStatus()).isEqualTo(StatusType.INACTIVE);
        assertThat(saved.getAcademicOfferingCycle()).isSameAs(courseCycle);
        assertThat(saved.getCreatedAt()).isNotNull();
        verifyNoInteractions(subjectRepository, subjectSectionRepository, slotRepository);
    }

    @Test
    void courseCycleSectionWithSlotsLinksTheSlotsToTheCourseSection() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(courseCycle));
        when(courseSubjectRepository.findById(3L)).thenReturn(Optional.of(new CourseSubject()));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(18)));
        persistCourseSections();

        service.createSection(CYCLE_ID, new CreateCycleSectionRequest("B", null, 3L, 18, List.of(slot(DayOfWeek.FRIDAY))));

        verify(conflictChecker).checkClassroomAvailability(term, CLASSROOM_ID, DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        var captor = ArgumentCaptor.forClass(SectionScheduleSlot.class);
        verify(slotRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getCourseSection().getId()).isEqualTo(200L);
        assertThat(saved.getSubjectSection()).isNull();
        assertThat(saved.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void courseCycleWithUnknownCourseSubjectFailsWithCourseSubjectNotFound() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(courseCycle));
        when(courseSubjectRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSection(CYCLE_ID, new CreateCycleSectionRequest("B", null, 3L, 18, null)))
                .isInstanceOf(CourseSubjectNotFoundException.class);

        verifyNoInteractions(courseSectionRepository);
    }

    @Test
    void courseCycleSlotInAClassroomTooSmallIsRejected() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(courseCycle));
        when(courseSubjectRepository.findById(3L)).thenReturn(Optional.of(new CourseSubject()));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.of(classroom(17)));
        persistCourseSections();

        assertThatThrownBy(() -> service.createSection(CYCLE_ID,
                new CreateCycleSectionRequest("B", null, 3L, 18, List.of(slot(DayOfWeek.FRIDAY)))))
                .isInstanceOf(SectionCapacityExceedsClassroomException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void courseCycleSlotWithUnknownClassroomFailsWithClassroomNotFound() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(courseCycle));
        when(courseSubjectRepository.findById(3L)).thenReturn(Optional.of(new CourseSubject()));
        when(classroomRepository.findById(CLASSROOM_ID)).thenReturn(Optional.empty());
        persistCourseSections();

        assertThatThrownBy(() -> service.createSection(CYCLE_ID,
                new CreateCycleSectionRequest("B", null, 3L, 18, List.of(slot(DayOfWeek.FRIDAY)))))
                .isInstanceOf(ClassroomNotFoundException.class);
    }

    @Test
    void rejectedCycleStillAcceptsNewSections() {
        programCycle.setStatus(AcademicOfferingCycleStatus.REJECTED);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(programCycle));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(SubjectStatus.ACTIVE)));
        persistSubjectSections();

        assertThat(service.createSection(CYCLE_ID, new CreateCycleSectionRequest("A", 1L, null, 10, null))).isEqualTo(100L);
    }
}
