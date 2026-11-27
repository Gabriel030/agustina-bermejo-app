package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.CurrentCurriculumPlanProvider;
import com.red_escuela.backend.academicstructure.api.CurriculumPlanEnrollmentActivityPort;
import com.red_escuela.backend.academicstructure.dto.AddCorequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.domain.CurriculumPlanLockedException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.academicstructure.repository.ElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectCorequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramSubjectLockedPlanTest {

    private ProgramSubjectRepository programSubjectRepository;
    private ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    private ProgramSubjectCorequisiteRepository corequisiteRepository;
    private CurriculumPlanRepository curriculumPlanRepository;
    private CurriculumPlanEnrollmentActivityPort enrollmentActivityPort;
    private ProgramSubjectService service;
    private Program program;

    @BeforeEach
    void setUp() {
        programSubjectRepository = Mockito.mock(ProgramSubjectRepository.class);
        prerequisiteRepository = Mockito.mock(ProgramSubjectPrerequisiteRepository.class);
        corequisiteRepository = Mockito.mock(ProgramSubjectCorequisiteRepository.class);
        curriculumPlanRepository = Mockito.mock(CurriculumPlanRepository.class);
        enrollmentActivityPort = Mockito.mock(CurriculumPlanEnrollmentActivityPort.class);
        program = Program.builder().id(1L).name("Enfermería").totalYears(3).build();
        when(enrollmentActivityPort.hasEnrolledStudentsInProgram(1L)).thenReturn(true);
        service = new ProgramSubjectService(programSubjectRepository, Mockito.mock(ProgramRepository.class),
                Mockito.mock(SubjectRepository.class), Mockito.mock(ElectiveGroupRepository.class),
                Mockito.mock(ProgramElectiveGroupRepository.class), prerequisiteRepository, corequisiteRepository,
                curriculumPlanRepository, Mockito.mock(CurrentCurriculumPlanProvider.class),
                new CurriculumPlanEditPolicy(enrollmentActivityPort));
    }

    private CurriculumPlan plan(long id, CurriculumPlanStatus status) {
        return CurriculumPlan.builder().id(id).program(program).versionNumber(1).name("Plan").status(status)
                .validFrom(LocalDate.of(2020, 3, 1)).build();
    }

    private ProgramSubject entry(long id, CurriculumPlan plan) {
        ProgramSubject entry = new ProgramSubject();
        entry.setId(id);
        entry.setProgram(program);
        entry.setPlan(plan);
        when(programSubjectRepository.findById(id)).thenReturn(Optional.of(entry));
        return entry;
    }

    private UpdateProgramSubjectRequest creditsChange() {
        return new UpdateProgramSubjectRequest(null, null, 12, null, null, null);
    }

    @Test
    void editingCreditsOfActivePlanWithEnrolledStudentsIsRejected() {
        entry(10L, plan(5L, CurriculumPlanStatus.ACTIVE));

        assertThatThrownBy(() -> service.update(10L, creditsChange())).isInstanceOf(CurriculumPlanLockedException.class);
        verify(programSubjectRepository, never()).save(any());
    }

    @Test
    void editingCreditsOfExpiredPlanWithEnrolledStudentsIsRejected() {
        entry(10L, plan(5L, CurriculumPlanStatus.EXPIRED));

        assertThatThrownBy(() -> service.update(10L, creditsChange())).isInstanceOf(CurriculumPlanLockedException.class);
    }

    @Test
    void editingCreditsOfDraftPlanIsAllowedEvenWhenTheProgramHasStudents() {
        ProgramSubject entry = entry(10L, plan(5L, CurriculumPlanStatus.DRAFT));
        when(programSubjectRepository.save(entry)).thenReturn(entry);

        assertThatCode(() -> service.update(10L, creditsChange())).doesNotThrowAnyException();
        assertThat(entry.getCredits()).isEqualTo(12);
    }

    @Test
    void editingCreditsOfActivePlanWithoutStudentsIsAllowed() {
        when(enrollmentActivityPort.hasEnrolledStudentsInProgram(1L)).thenReturn(false);
        ProgramSubject entry = entry(10L, plan(5L, CurriculumPlanStatus.ACTIVE));
        when(programSubjectRepository.save(entry)).thenReturn(entry);

        assertThatCode(() -> service.update(10L, creditsChange())).doesNotThrowAnyException();
    }

    @Test
    void deactivatingAndReactivatingSubjectOfLockedPlanIsRejected() {
        entry(10L, plan(5L, CurriculumPlanStatus.ACTIVE));

        assertThatThrownBy(() -> service.deactivate(10L)).isInstanceOf(CurriculumPlanLockedException.class);
        assertThatThrownBy(() -> service.reactivate(10L)).isInstanceOf(CurriculumPlanLockedException.class);
    }

    @Test
    void addingSubjectToLockedPlanIsRejected() {
        CurriculumPlan locked = plan(5L, CurriculumPlanStatus.ACTIVE);
        when(curriculumPlanRepository.findById(5L)).thenReturn(Optional.of(locked));
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                99L, ProgramSubjectCategory.ADDITIONAL_MANDATORY, null, null, 10, null, null, null);

        assertThatThrownBy(() -> service.addSubjectToPlan(5L, request)).isInstanceOf(CurriculumPlanLockedException.class);
    }

    @Test
    void addingPrerequisiteInLockedPlanIsRejected() {
        CurriculumPlan locked = plan(5L, CurriculumPlanStatus.ACTIVE);
        entry(10L, locked);
        entry(11L, locked);

        assertThatThrownBy(() -> service.addPrerequisite(10L, new AddPrerequisiteRequest(11L, RequirementType.APPROVED, null, null)))
                .isInstanceOf(CurriculumPlanLockedException.class);
    }

    @Test
    void removingPrerequisiteInLockedPlanIsRejected() {
        ProgramSubjectPrerequisite link = new ProgramSubjectPrerequisite();
        link.setProgramSubject(entry(10L, plan(5L, CurriculumPlanStatus.ACTIVE)));
        when(prerequisiteRepository.findById(3L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.removePrerequisite(3L)).isInstanceOf(CurriculumPlanLockedException.class);
        verify(prerequisiteRepository, never()).delete(any());
    }

    @Test
    void addingCorequisiteInLockedPlanIsRejected() {
        CurriculumPlan locked = plan(5L, CurriculumPlanStatus.ACTIVE);
        entry(10L, locked);
        entry(11L, locked);

        assertThatThrownBy(() -> service.addCorequisite(10L, new AddCorequisiteRequest(11L)))
                .isInstanceOf(CurriculumPlanLockedException.class);
    }

    @Test
    void removingCorequisiteInLockedPlanIsRejected() {
        ProgramSubjectCorequisite link = new ProgramSubjectCorequisite();
        link.setProgramSubject(entry(10L, plan(5L, CurriculumPlanStatus.ACTIVE)));
        when(corequisiteRepository.findById(4L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.removeCorequisite(4L)).isInstanceOf(CurriculumPlanLockedException.class);
        verify(corequisiteRepository, never()).delete(any());
    }
}
