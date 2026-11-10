package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateAcademicOfferingCycleRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleAlreadyInProgressException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.exception.HolidaysNotLoadedForYearException;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleDrafter;
import com.red_escuela.backend.academicstructure.service.HolidayYearGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAcademicOfferingCycleServiceTest {

    private static final Long TERM_ID = 2L;
    private static final Long PROGRAM_ID = 6L;
    private static final Long COURSE_ID = 8L;

    @Mock
    private AcademicOfferingCycleRepository cycleRepository;
    @Mock
    private TermRepository termRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private AcademicOfferingCycleDrafter cycleDrafter;
    @Mock
    private HolidayYearGuard holidayYearGuard;

    private CreateAcademicOfferingCycleService service;
    private Term term;
    private Person creator;

    @BeforeEach
    void setUp() {
        service = new CreateAcademicOfferingCycleService(cycleRepository, termRepository, programRepository, courseRepository, cycleDrafter, holidayYearGuard);
        term = TermFixtures.newTerm();
        term.setId(TERM_ID);
        term.setDescription("2026");
        creator = new Person();
        creator.setName("Ana");
        creator.setLastname("Diaz");
    }

    private AcademicOfferingCycle existingCycle(Long termId, AcademicOfferingCycleStatus status) {
        var otherTerm = TermFixtures.newTerm();
        otherTerm.setId(termId);
        var cycle = new AcademicOfferingCycle();
        cycle.setTerm(otherTerm);
        cycle.setStatus(status);
        return cycle;
    }

    private void stubDraft() {
        when(cycleDrafter.draftFor(term)).thenAnswer(invocation -> {
            var draft = new AcademicOfferingCycle();
            draft.setTerm(term);
            draft.setCreatedBy(creator);
            draft.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
            return draft;
        });
    }

    private void stubTermAndPersistence() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        stubDraft();
        when(cycleRepository.save(any(AcademicOfferingCycle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestWithoutProgramNorCourseFailsAsBadRequestAndTouchesNoRepository() {
        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(null, null, TERM_ID)))
                .isInstanceOf(RequestFieldRequiredException.class);

        verify(courseRepository, never()).findById(any());
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void unknownTermFailsWithTermNotFoundAndCreatesNothing() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID)))
                .isInstanceOf(TermNotFoundException.class);

        verify(cycleRepository, never()).save(any());
    }

    @Test
    void programCycleIsCreatedAsDraftWithTheCurrentPersonAsCreator() {
        stubTermAndPersistence();
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(Program.builder().id(PROGRAM_ID).name("Nursing").build()));
        when(cycleRepository.findByProgramIdOrderByCreatedAtDesc(PROGRAM_ID)).thenReturn(List.of());

        var response = service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID));

        var captor = ArgumentCaptor.forClass(AcademicOfferingCycle.class);
        verify(cycleRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AcademicOfferingCycleStatus.DRAFT);
        assertThat(captor.getValue().getTerm()).isSameAs(term);
        assertThat(captor.getValue().getCreatedBy()).isSameAs(creator);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getCourse()).isNull();
        assertThat(response.programId()).isEqualTo(PROGRAM_ID);
        assertThat(response.programName()).isEqualTo("Nursing");
        assertThat(response.courseId()).isNull();
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    void programCycleWithUnknownProgramFailsWithProgramNotFound() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        stubDraft();
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID)))
                .isInstanceOf(ProgramNotFoundException.class);

        verify(cycleRepository, never()).save(any());
    }

    @Test
    void programWithADraftCycleInTheSameTermCannotOpenAnotherOne() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        stubDraft();
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(Program.builder().id(PROGRAM_ID).build()));
        when(cycleRepository.findByProgramIdOrderByCreatedAtDesc(PROGRAM_ID))
                .thenReturn(List.of(existingCycle(TERM_ID, AcademicOfferingCycleStatus.DRAFT)));

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID)))
                .isInstanceOf(CycleAlreadyInProgressException.class);

        verify(cycleRepository, never()).save(any());
    }

    @Test
    void programWithAPendingApprovalCycleInTheSameTermCannotOpenAnotherOne() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        stubDraft();
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(Program.builder().id(PROGRAM_ID).build()));
        when(cycleRepository.findByProgramIdOrderByCreatedAtDesc(PROGRAM_ID))
                .thenReturn(List.of(existingCycle(TERM_ID, AcademicOfferingCycleStatus.PENDING_APPROVAL)));

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID)))
                .isInstanceOf(CycleAlreadyInProgressException.class);
    }

    @Test
    void programCycleIgnoresInProgressCyclesOfOtherTermsAndFinishedCyclesOfTheSameTerm() {
        stubTermAndPersistence();
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(Program.builder().id(PROGRAM_ID).build()));
        when(cycleRepository.findByProgramIdOrderByCreatedAtDesc(PROGRAM_ID)).thenReturn(List.of(
                existingCycle(99L, AcademicOfferingCycleStatus.DRAFT),
                existingCycle(99L, AcademicOfferingCycleStatus.PENDING_APPROVAL),
                existingCycle(TERM_ID, AcademicOfferingCycleStatus.APPROVED),
                existingCycle(TERM_ID, AcademicOfferingCycleStatus.REJECTED),
                existingCycle(TERM_ID, AcademicOfferingCycleStatus.CLOSED)));

        service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID));

        verify(cycleRepository).save(any(AcademicOfferingCycle.class));
    }

    @Test
    void courseCycleIsCreatedAsDraftForTheCourse() {
        stubTermAndPersistence();
        var course = new Course();
        course.setId(COURSE_ID);
        course.setName("English");
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(cycleRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID)).thenReturn(List.of());

        var response = service.create(new CreateAcademicOfferingCycleRequest(null, COURSE_ID, TERM_ID));

        var captor = ArgumentCaptor.forClass(AcademicOfferingCycle.class);
        verify(cycleRepository).save(captor.capture());
        assertThat(captor.getValue().getCourse()).isSameAs(course);
        assertThat(captor.getValue().getProgram()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(AcademicOfferingCycleStatus.DRAFT);
        assertThat(response.courseId()).isEqualTo(COURSE_ID);
        assertThat(response.courseName()).isEqualTo("English");
        assertThat(response.programId()).isNull();
    }

    @Test
    void courseCycleWithUnknownCourseFailsWithCourseNotFound() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        stubDraft();
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(null, COURSE_ID, TERM_ID)))
                .isInstanceOf(CourseNotFoundException.class);

        verify(cycleRepository, never()).save(any());
    }

    @Test
    void courseWithADraftCycleInTheSameTermCannotOpenAnotherOne() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        stubDraft();
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(new Course()));
        when(cycleRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
                .thenReturn(List.of(existingCycle(TERM_ID, AcademicOfferingCycleStatus.DRAFT)));

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(null, COURSE_ID, TERM_ID)))
                .isInstanceOf(CycleAlreadyInProgressException.class);

        verify(cycleRepository, never()).save(any());
    }

    @Test
    void courseWithAPendingApprovalCycleInTheSameTermCannotOpenAnotherOne() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        stubDraft();
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(new Course()));
        when(cycleRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
                .thenReturn(List.of(existingCycle(TERM_ID, AcademicOfferingCycleStatus.PENDING_APPROVAL)));

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(null, COURSE_ID, TERM_ID)))
                .isInstanceOf(CycleAlreadyInProgressException.class);
    }

    @Test
    void courseCycleIgnoresInProgressCyclesOfOtherTermsAndFinishedCyclesOfTheSameTerm() {
        stubTermAndPersistence();
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(new Course()));
        when(cycleRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID)).thenReturn(List.of(
                existingCycle(99L, AcademicOfferingCycleStatus.DRAFT),
                existingCycle(99L, AcademicOfferingCycleStatus.PENDING_APPROVAL),
                existingCycle(TERM_ID, AcademicOfferingCycleStatus.APPROVED),
                existingCycle(TERM_ID, AcademicOfferingCycleStatus.REJECTED),
                existingCycle(TERM_ID, AcademicOfferingCycleStatus.CLOSED)));

        service.create(new CreateAcademicOfferingCycleRequest(null, COURSE_ID, TERM_ID));

        verify(cycleRepository).save(any(AcademicOfferingCycle.class));
    }

    @Test
    void termOfAYearWithoutLoadedHolidaysBlocksTheCycleBeforeAnyLookupOrSave() {
        when(termRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        doThrow(new HolidaysNotLoadedForYearException(2077)).when(holidayYearGuard).ensureHolidaysLoadedForTerm(term);

        assertThatThrownBy(() -> service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID)))
                .isInstanceOf(HolidaysNotLoadedForYearException.class);

        verify(cycleDrafter, never()).draftFor(any());
        verify(programRepository, never()).findById(any());
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void termIsCheckedAgainstTheHolidayGuardAndTheCycleIsCreated() {
        stubTermAndPersistence();
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(Program.builder().id(PROGRAM_ID).build()));
        when(cycleRepository.findByProgramIdOrderByCreatedAtDesc(PROGRAM_ID)).thenReturn(List.of());

        service.create(new CreateAcademicOfferingCycleRequest(PROGRAM_ID, null, TERM_ID));

        verify(holidayYearGuard).ensureHolidaysLoadedForTerm(term);
        verify(cycleRepository).save(any(AcademicOfferingCycle.class));
    }
}
