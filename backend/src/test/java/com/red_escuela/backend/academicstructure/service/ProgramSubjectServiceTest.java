package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.RequirementType;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.exception.CircularPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotAssignedException;
import com.red_escuela.backend.academicstructure.exception.InvalidPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectAlreadyExistsException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import com.red_escuela.backend.academicstructure.service.ElectiveGroupService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramSubjectServiceTest extends AbstractIntegrationTest {
    @Autowired
    private ProgramSubjectService programSubjectService;

    @Autowired
    private ElectiveGroupService electiveGroupService;

    @Autowired
    private EntityManager entityManager;

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(4);
        entityManager.persist(program);
        return program;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private ProgramSubject addSubject(Program program, Subject subject, int year) {
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), year, CurricularTerm.FIRST, 10, null, null, null);
        return programSubjectService.addSubject(program.getId(), request);
    }

    private ProgramSubject addSubjectWithoutYear(Program program, Subject subject) {
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), null, null, 10, null, null, null);
        return programSubjectService.addSubject(program.getId(), request);
    }

    @Test
    void sameSubjectInTwoProgramsCreatesTwoIndependentEntries() {
        Subject ingles = persistSubject("Inglés I");
        Program carreraA = persistProgram("Tecnicatura A");
        Program carreraB = persistProgram("Tecnicatura B");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(carreraA, ingles, 1);
        ProgramSubject entradaB = addSubject(carreraB, ingles, 2);

        assertThat(entradaA.getId()).isNotEqualTo(entradaB.getId());
        assertThat(entradaA.getSubject().getId()).isEqualTo(entradaB.getSubject().getId());
        assertThat(entradaA.getProgram().getId()).isNotEqualTo(entradaB.getProgram().getId());
    }

    @Test
    void addingTheSameSubjectTwiceToTheSameProgramFails() {
        Subject subject = persistSubject("Física I");
        Program program = persistProgram("Tecnicatura C");
        entityManager.flush();

        addSubject(program, subject, 1);

        assertThatThrownBy(() -> addSubject(program, subject, 1))
                .isInstanceOf(ProgramSubjectAlreadyExistsException.class);
    }

    @Test
    void taggingWithElectiveGroupWithoutAssignedRangeFails() {
        Subject subject = persistSubject("Portugués I");
        Program program = persistProgram("Tecnicatura D");
        ElectiveGroup group = new ElectiveGroup();
        group.setName("Idiomas");
        entityManager.persist(group);
        entityManager.flush();

        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), 1, CurricularTerm.FIRST, 5, null, null, group.getId());

        assertThatThrownBy(() -> programSubjectService.addSubject(program.getId(), request))
                .isInstanceOf(ElectiveGroupNotAssignedException.class);

        electiveGroupService.assignToProgram(program.getId(),
                new com.red_escuela.backend.academicstructure.dto.AssignElectiveGroupRequest(group.getId(), 1, 2));

        ProgramSubject created = programSubjectService.addSubject(program.getId(), request);
        assertThat(created.getElectiveGroup().getId()).isEqualTo(group.getId());
    }

    @Test
    void prerequisiteBetweenDifferentProgramsFails() {
        Program carreraA = persistProgram("Tecnicatura E");
        Program carreraB = persistProgram("Tecnicatura F");
        Subject subjectA = persistSubject("Álgebra");
        Subject subjectB = persistSubject("Geometría");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(carreraA, subjectA, 1);
        ProgramSubject entradaB = addSubject(carreraB, subjectB, 1);

        AddPrerequisiteRequest request = new AddPrerequisiteRequest(
                entradaB.getId(), RequirementType.APPROVED, null, null);

        assertThatThrownBy(() -> programSubjectService.addPrerequisite(entradaA.getId(), request))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    @Test
    void prerequisiteThatCreatesATwoStepCycleFails() {
        Program program = persistProgram("Tecnicatura G");
        Subject subjectA = persistSubject("Materia A");
        Subject subjectB = persistSubject("Materia B");
        entityManager.flush();

        ProgramSubject entradaA = addSubjectWithoutYear(program, subjectA);
        ProgramSubject entradaB = addSubjectWithoutYear(program, subjectB);

        programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));

        assertThatThrownBy(() -> programSubjectService.addPrerequisite(entradaB.getId(),
                new AddPrerequisiteRequest(entradaA.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(CircularPrerequisiteException.class);
    }

    @Test
    void prerequisiteThatCreatesAThreeStepCycleFails() {
        Program program = persistProgram("Tecnicatura H");
        Subject subjectA = persistSubject("Materia X");
        Subject subjectB = persistSubject("Materia Y");
        Subject subjectC = persistSubject("Materia Z");
        entityManager.flush();

        ProgramSubject entradaA = addSubjectWithoutYear(program, subjectA);
        ProgramSubject entradaB = addSubjectWithoutYear(program, subjectB);
        ProgramSubject entradaC = addSubjectWithoutYear(program, subjectC);

        programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));
        programSubjectService.addPrerequisite(entradaB.getId(),
                new AddPrerequisiteRequest(entradaC.getId(), RequirementType.APPROVED, null, null));

        assertThatThrownBy(() -> programSubjectService.addPrerequisite(entradaC.getId(),
                new AddPrerequisiteRequest(entradaA.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(CircularPrerequisiteException.class);
    }

    @Test
    void addingSubjectLeavesStatusActive() {
        Subject subject = persistSubject("Materia con status");
        Program program = persistProgram("Tecnicatura I");
        entityManager.flush();

        ProgramSubject created = addSubject(program, subject, 1);

        assertThat(created.getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void removingNonExistentPrerequisiteFails() {
        assertThatThrownBy(() -> programSubjectService.removePrerequisite(999999L))
                .isInstanceOf(PrerequisiteNotFoundException.class);
    }

    private ProgramSubject addSubject(Program program, Subject subject, int year, CurricularTerm term) {
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), year, term, 10, null, null, null);
        return programSubjectService.addSubject(program.getId(), request);
    }

    @Test
    void prerequisiteBetweenTwoAnnualSubjectsOfTheSameYearFails() {
        Program program = persistProgram("Tecnicatura correlativas 1");
        Subject subjectA = persistSubject("Anual A");
        Subject subjectB = persistSubject("Anual B");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(program, subjectA, 1, CurricularTerm.ANNUAL);
        ProgramSubject entradaB = addSubject(program, subjectB, 1, CurricularTerm.ANNUAL);

        assertThatThrownBy(() -> programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    @Test
    void prerequisiteBetweenTwoSubjectsOfTheSameYearAndTermFails() {
        Program program = persistProgram("Tecnicatura correlativas 2");
        Subject subjectA = persistSubject("1C A");
        Subject subjectB = persistSubject("1C B");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(program, subjectA, 1, CurricularTerm.FIRST);
        ProgramSubject entradaB = addSubject(program, subjectB, 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    @Test
    void prerequisiteWithThePreviousTermOfTheSameYearWorks() {
        Program program = persistProgram("Tecnicatura correlativas 3");
        Subject subjectA = persistSubject("2C A");
        Subject subjectB = persistSubject("1C previa");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(program, subjectA, 1, CurricularTerm.SECOND);
        ProgramSubject entradaB = addSubject(program, subjectB, 1, CurricularTerm.FIRST);

        var creada = programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));

        assertThat(creada.getPrerequisiteProgramSubject().getId()).isEqualTo(entradaB.getId());
    }

    @Test
    void prerequisiteWithThePreviousYearSameTermWorks() {
        Program program = persistProgram("Tecnicatura correlativas 4");
        Subject subjectA = persistSubject("Año 2 1C");
        Subject subjectB = persistSubject("Año 1 1C");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(program, subjectA, 2, CurricularTerm.FIRST);
        ProgramSubject entradaB = addSubject(program, subjectB, 1, CurricularTerm.FIRST);

        var creada = programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));

        assertThat(creada.getPrerequisiteProgramSubject().getId()).isEqualTo(entradaB.getId());
    }

    @Test
    void prerequisiteBetweenAnnualAndSecondTermOfTheSameYearFails() {
        Program program = persistProgram("Tecnicatura correlativas 5");
        Subject subjectA = persistSubject("2C del año");
        Subject subjectB = persistSubject("Anual del año");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(program, subjectA, 1, CurricularTerm.SECOND);
        ProgramSubject entradaB = addSubject(program, subjectB, 1, CurricularTerm.ANNUAL);

        assertThatThrownBy(() -> programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    @Test
    void prerequisiteWithALaterSubjectFails() {
        Program program = persistProgram("Tecnicatura correlativas 6");
        Subject subjectA = persistSubject("1C que no puede depender del 2C");
        Subject subjectB = persistSubject("2C posterior");
        entityManager.flush();

        ProgramSubject entradaA = addSubject(program, subjectA, 1, CurricularTerm.FIRST);
        ProgramSubject entradaB = addSubject(program, subjectB, 1, CurricularTerm.SECOND);

        assertThatThrownBy(() -> programSubjectService.addPrerequisite(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }
}
