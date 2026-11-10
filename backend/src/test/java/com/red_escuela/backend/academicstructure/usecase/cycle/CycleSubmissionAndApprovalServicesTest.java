package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleApprovedEvent;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleRejectedEvent;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleSubmittedEvent;
import com.red_escuela.backend.academicstructure.exception.CycleDecisionIdempotencyKeyConflictException;
import com.red_escuela.backend.academicstructure.exception.CycleDecisionIdempotencyKeyInvalidException;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CycleIncompleteException;
import com.red_escuela.backend.academicstructure.exception.CycleNotEditableException;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleNotPendingApprovalException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.service.CycleApproval;
import com.red_escuela.backend.academicstructure.service.CycleRejection;
import com.red_escuela.backend.academicstructure.service.CycleSubmission;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleReader;
import com.red_escuela.backend.academicstructure.service.SectionConflictChecker;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleReviewer;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleSubmissionAndApprovalServicesTest {

    private static final Long CYCLE_ID = 5L;

    @Mock
    private AcademicOfferingCycleRepository cycleRepository;
    @Mock
    private SubjectSectionRepository subjectSectionRepository;
    @Mock
    private CourseSubjectSectionRepository courseSectionRepository;
    @Mock
    private SectionScheduleSlotRepository slotRepository;
    @Mock
    private SectionConflictChecker conflictChecker;
    @Mock
    private CurrentActorFinder currentActorFinder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AcademicOfferingCycle cycle;
    private Term term;
    private Person creator;

    @BeforeEach
    void setUp() {
        term = TermFixtures.newTerm();
        term.setId(2L);
        cycle = new AcademicOfferingCycle();
        cycle.setId(CYCLE_ID);
        cycle.setTerm(term);
        cycle.setProgram(Program.builder().name("Tecnicatura").build());
        creator = new Person();
        creator.setId(7L);
        cycle.setCreatedBy(creator);
    }

    private SubmitCycleForApprovalService submitService() {
        return new SubmitCycleForApprovalService(new CycleSubmission(cycleRepository, subjectSectionRepository, courseSectionRepository, currentActorFinder, Clock.systemDefaultZone(), eventPublisher));
    }

    private ApproveCycleService approveService() {
        return new ApproveCycleService(new CycleApproval(cycleRepository, subjectSectionRepository, courseSectionRepository,
                slotRepository, conflictChecker,
                new AcademicOfferingCycleReviewer(currentActorFinder, Clock.systemDefaultZone()),
                eventPublisher));
    }

    private SubjectSection subjectSection(Long id, InstructorRole... roles) {
        var section = new SubjectSection();
        section.setId(id);
        section.setStatus(StatusType.INACTIVE);
        section.setInstructors(java.util.Arrays.stream(roles).map(role -> {
            var instructor = new SubjectSectionInstructor();
            instructor.setRole(role);
            return instructor;
        }).toList());
        return section;
    }

    private CourseSubjectSection courseSection(Long id, InstructorRole... roles) {
        var section = new CourseSubjectSection();
        section.setId(id);
        section.setStatus(StatusType.INACTIVE);
        section.setInstructors(java.util.Arrays.stream(roles).map(role -> {
            var instructor = new CourseSubjectSectionInstructor();
            instructor.setRole(role);
            return instructor;
        }).toList());
        return section;
    }

    private SectionScheduleSlot slot(Long id, Long classroomId, DayOfWeek day) {
        var classroom = new Classroom();
        classroom.setId(classroomId);
        var slot = new SectionScheduleSlot();
        slot.setId(id);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        return slot;
    }

    @Test
    void submittingAnUnknownCycleFailsWithCycleNotFound() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submitService().submit(CYCLE_ID)).isInstanceOf(CycleNotFoundException.class);
    }

    @Test
    void submittingACycleThatIsNotEditableIsRejectedBeforeLookingAtSections() {
        cycle.setStatus(AcademicOfferingCycleStatus.APPROVED);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> submitService().submit(CYCLE_ID)).isInstanceOf(CycleNotEditableException.class);

        verify(cycleRepository, never()).save(any());
        verify(subjectSectionRepository, never()).findByAcademicOfferingCycleId(any());
    }

    @Test
    void submittingACycleWithoutSectionsIsRejectedAsIncomplete() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> submitService().submit(CYCLE_ID))
                .isInstanceOf(CycleIncompleteException.class)
                .hasMessageContaining("no tiene comisiones");

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.DRAFT);
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void submittingWithASubjectSectionWithoutLeadInstructorIsRejected() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID))
                .thenReturn(List.of(subjectSection(1L, InstructorRole.LEAD), subjectSection(2L)));
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> submitService().submit(CYCLE_ID))
                .isInstanceOf(CycleIncompleteException.class)
                .hasMessageContaining("sin docente titular");

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.DRAFT);
    }

    @Test
    void submittingWithASubjectSectionWhoseOnlyInstructorIsNotLeadIsRejected() {
        var nonLeadRole = java.util.Arrays.stream(InstructorRole.values()).filter(role -> role != InstructorRole.LEAD).findFirst().orElseThrow();
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(subjectSection(1L, nonLeadRole)));
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> submitService().submit(CYCLE_ID)).isInstanceOf(CycleIncompleteException.class);
    }

    @Test
    void submittingWithACourseSectionWithoutLeadInstructorIsRejected() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID))
                .thenReturn(List.of(courseSection(1L, InstructorRole.LEAD), courseSection(2L)));

        assertThatThrownBy(() -> submitService().submit(CYCLE_ID))
                .isInstanceOf(CycleIncompleteException.class)
                .hasMessageContaining("sin docente titular");

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.DRAFT);
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void submittingACompleteProgramCycleMovesItToPendingApproval() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(subjectSection(1L, InstructorRole.LEAD)));
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());

        submitService().submit(CYCLE_ID);

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        verify(cycleRepository).save(cycle);
    }

    @Test
    void submittingACompleteCourseCycleMovesItToPendingApproval() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(courseSection(1L, InstructorRole.LEAD)));

        submitService().submit(CYCLE_ID);

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        verify(cycleRepository).save(cycle);
    }

    @Test
    void submittingARejectedCycleClearsTheDecisionData() {
        cycle.setStatus(AcademicOfferingCycleStatus.REJECTED);
        cycle.setRejectionReason("missing teachers");
        cycle.setDecidedBy(new Person());
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(subjectSection(1L, InstructorRole.LEAD)));
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());

        submitService().submit(CYCLE_ID);

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        assertThat(cycle.getRejectionReason()).isNull();
        assertThat(cycle.getDecidedBy()).isNull();
    }

    @Test
    void approvingAnUnknownCycleFailsWithCycleNotFound() {
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approveService().approve(CYCLE_ID, null)).isInstanceOf(CycleNotFoundException.class);
    }

    @Test
    void approvingACycleThatIsNotPendingApprovalIsRejected() {
        for (var status : new AcademicOfferingCycleStatus[]{AcademicOfferingCycleStatus.DRAFT, AcademicOfferingCycleStatus.APPROVED, AcademicOfferingCycleStatus.REJECTED}) {
            cycle.setStatus(status);
            when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));

            assertThatThrownBy(() -> approveService().approve(CYCLE_ID, null)).isInstanceOf(CycleNotPendingApprovalException.class);
        }

        verify(cycleRepository, never()).save(any());
        verify(subjectSectionRepository, never()).findByAcademicOfferingCycleId(any());
    }

    @Test
    void approvingACycleActivatesAllItsSectionsAndRecordsTheDirectorDecision() {
        cycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var director = new Person();
        var subjectSection = subjectSection(1L, InstructorRole.LEAD);
        var courseSection = courseSection(2L, InstructorRole.LEAD);
        var subjectSlot = slot(10L, 3L, DayOfWeek.MONDAY);
        var courseSlot = slot(11L, 4L, DayOfWeek.TUESDAY);
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(subjectSection));
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(courseSection));
        when(slotRepository.findBySubjectSectionId(1L)).thenReturn(List.of(subjectSlot));
        when(slotRepository.findByCourseSectionId(2L)).thenReturn(List.of(courseSlot));
        when(currentActorFinder.currentPerson()).thenReturn(director);

        approveService().approve(CYCLE_ID, null);

        verify(conflictChecker).checkClassroomAvailability(term, 3L, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), 10L);
        verify(conflictChecker).checkClassroomAvailability(term, 4L, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), 11L);
        assertThat(subjectSection.getStatus()).isEqualTo(StatusType.ACTIVE);
        assertThat(courseSection.getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(subjectSectionRepository).save(subjectSection);
        verify(courseSectionRepository).save(courseSection);
        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.APPROVED);
        assertThat(cycle.getDecidedBy()).isSameAs(director);
        assertThat(cycle.getDecidedAt()).isNotNull();
        verify(cycleRepository).save(cycle);
    }

    @Test
    void approvalFailsAndActivatesNothingWhenASubjectSlotConflictsWithAnotherReservation() {
        cycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var subjectSection = subjectSection(1L, InstructorRole.LEAD);
        var subjectSlot = slot(10L, 3L, DayOfWeek.MONDAY);
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(subjectSection));
        when(slotRepository.findBySubjectSectionId(1L)).thenReturn(List.of(subjectSlot));
        doThrow(new IllegalStateException("occupied")).when(conflictChecker)
                .checkClassroomAvailability(term, 3L, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), 10L);

        assertThatThrownBy(() -> approveService().approve(CYCLE_ID, null)).isInstanceOf(IllegalStateException.class);

        assertThat(subjectSection.getStatus()).isEqualTo(StatusType.INACTIVE);
        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        verify(subjectSectionRepository, never()).save(any());
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void approvalFailsAndActivatesNothingWhenACourseSlotConflictsWithAnotherReservation() {
        cycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        var courseSection = courseSection(2L, InstructorRole.LEAD);
        var courseSlot = slot(11L, 4L, DayOfWeek.TUESDAY);
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(courseSection));
        when(slotRepository.findByCourseSectionId(2L)).thenReturn(List.of(courseSlot));
        doThrow(new IllegalStateException("occupied")).when(conflictChecker)
                .checkClassroomAvailability(term, 4L, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), 11L);

        assertThatThrownBy(() -> approveService().approve(CYCLE_ID, null)).isInstanceOf(IllegalStateException.class);

        assertThat(courseSection.getStatus()).isEqualTo(StatusType.INACTIVE);
        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        verify(courseSectionRepository, never()).save(any());
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void getCycleSectionsDelegatesToTheReaderAndReturnsItsResult() {
        var reader = mock(AcademicOfferingCycleReader.class);
        var sections = List.of(mock(CycleSectionResponse.class));
        when(reader.listCycleSections(CYCLE_ID)).thenReturn(sections);

        assertThat(new GetCycleSectionsService(reader).getSections(CYCLE_ID)).isSameAs(sections);
    }

    private RejectCycleService rejectService() {
        return new RejectCycleService(new CycleRejection(cycleRepository,
                new AcademicOfferingCycleReviewer(currentActorFinder, Clock.systemDefaultZone()), eventPublisher));
    }

    private void givenPendingCycleWithOneSection() {
        cycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());
        when(currentActorFinder.currentPerson()).thenReturn(new Person());
    }

    @Test
    void submittingRecordsTheSubmitterAndPublishesTheSubmittedEvent() {
        var submitter = new Person();
        submitter.setId(9L);
        when(currentActorFinder.currentPerson()).thenReturn(submitter);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(subjectSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of(subjectSection(1L, InstructorRole.LEAD)));
        when(courseSectionRepository.findByAcademicOfferingCycleId(CYCLE_ID)).thenReturn(List.of());

        submitService().submit(CYCLE_ID);

        assertThat(cycle.getSubmittedBy()).isSameAs(submitter);
        assertThat(cycle.getSubmittedAt()).isNotNull();
        var captor = ArgumentCaptor.forClass(AcademicOfferingCycleSubmittedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().cycleId()).isEqualTo(CYCLE_ID);
        assertThat(captor.getValue().targetName()).isEqualTo("Tecnicatura");
    }

    @Test
    void approvingPublishesTheApprovedEventForTheSubmitter() {
        var submitter = new Person();
        submitter.setId(9L);
        cycle.setSubmittedBy(submitter);
        givenPendingCycleWithOneSection();

        approveService().approve(CYCLE_ID, "key-1");

        var captor = ArgumentCaptor.forClass(AcademicOfferingCycleApprovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().submitterPersonId()).isEqualTo(9L);
        assertThat(cycle.getDecisionIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void approvingACycleWithoutRecordedSubmitterNotifiesTheCreator() {
        givenPendingCycleWithOneSection();

        approveService().approve(CYCLE_ID, null);

        var captor = ArgumentCaptor.forClass(AcademicOfferingCycleApprovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().submitterPersonId()).isEqualTo(7L);
    }

    @Test
    void retryingAnApprovalWithTheSameKeyDoesNothingAndPublishesNothing() {
        cycle.setStatus(AcademicOfferingCycleStatus.APPROVED);
        cycle.setDecisionIdempotencyKey("key-1");
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));

        approveService().approve(CYCLE_ID, " key-1 ");

        verify(cycleRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(subjectSectionRepository, never()).findByAcademicOfferingCycleId(any());
    }

    @Test
    void approvingWithAKeyAlreadyUsedByARejectionIsAKeyConflict() {
        cycle.setStatus(AcademicOfferingCycleStatus.REJECTED);
        cycle.setDecisionIdempotencyKey("key-1");
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> approveService().approve(CYCLE_ID, "key-1"))
                .isInstanceOf(CycleDecisionIdempotencyKeyConflictException.class);
    }

    @Test
    void approvingAnAlreadyApprovedCycleWithADifferentKeyIsNotPendingApproval() {
        cycle.setStatus(AcademicOfferingCycleStatus.APPROVED);
        cycle.setDecisionIdempotencyKey("key-1");
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> approveService().approve(CYCLE_ID, "key-2"))
                .isInstanceOf(CycleNotPendingApprovalException.class);
    }

    @Test
    void aKeyLongerThanOneHundredCharactersIsInvalid() {
        var tooLong = "k".repeat(101);

        assertThatThrownBy(() -> approveService().approve(CYCLE_ID, tooLong))
                .isInstanceOf(CycleDecisionIdempotencyKeyInvalidException.class);
        assertThatThrownBy(() -> rejectService().reject(CYCLE_ID, "motivo suficiente", tooLong))
                .isInstanceOf(CycleDecisionIdempotencyKeyInvalidException.class);
    }

    @Test
    void rejectingPublishesTheRejectedEventWithTheReason() {
        cycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(currentActorFinder.currentPerson()).thenReturn(new Person());

        rejectService().reject(CYCLE_ID, "faltan docentes", "key-9");

        var captor = ArgumentCaptor.forClass(AcademicOfferingCycleRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().rejectionReason()).isEqualTo("faltan docentes");
        assertThat(captor.getValue().submitterPersonId()).isEqualTo(7L);
        verify(cycleRepository).save(cycle);
    }

    @Test
    void retryingARejectionWithTheSameKeyDoesNothing() {
        cycle.setStatus(AcademicOfferingCycleStatus.REJECTED);
        cycle.setDecisionIdempotencyKey("key-9");
        when(cycleRepository.findByIdForUpdate(CYCLE_ID)).thenReturn(Optional.of(cycle));

        rejectService().reject(CYCLE_ID, "faltan docentes", "key-9");

        verify(cycleRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
