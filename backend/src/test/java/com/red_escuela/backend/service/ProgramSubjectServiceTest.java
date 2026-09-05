package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.ElectiveGroup;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.RequirementType;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.CircularPrerequisiteException;
import com.red_escuela.backend.exception.ElectiveGroupNotAssignedException;
import com.red_escuela.backend.exception.InvalidPrerequisiteException;
import com.red_escuela.backend.exception.PrerequisiteNotFoundException;
import com.red_escuela.backend.exception.ProgramSubjectAlreadyExistsException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private ProgramSubject agregarMateria(Program program, Subject subject, int year) {
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), year, CurricularTerm.FIRST, 10, null, null, null);
        return programSubjectService.agregarMateria(program.getId(), request);
    }

    // Sin año/cuatrimestre curricular: agregarMateria() bypasea la validación @NotNull
    // del DTO (esa solo corre en la capa web, vía @Valid) — usado para aislar los tests
    // de detección de ciclos de la validación de orden cronológico entre correlativas
    // (ver ProgramSubjectService.validateChronologicalOrder): con año/cuatrimestre reales,
    // un ciclo A->B->A es imposible de construir (exigiría que B preceda a A Y que A
    // preceda a B a la vez), así que estos tests necesitan materias sin esa restricción
    // para poder ejercitar el DFS de ciclos en sí mismo.
    private ProgramSubject agregarMateriaSinAnio(Program program, Subject subject) {
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), null, null, 10, null, null, null);
        return programSubjectService.agregarMateria(program.getId(), request);
    }

    @Test
    void sameSubjectInTwoProgramsCreatesTwoEntriesIndependent() {
        Subject ingles = persistSubject("Inglés I");
        Program carreraA = persistProgram("Tecnicatura A");
        Program carreraB = persistProgram("Tecnicatura B");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(carreraA, ingles, 1);
        ProgramSubject entradaB = agregarMateria(carreraB, ingles, 2);

        assertThat(entradaA.getId()).isNotEqualTo(entradaB.getId());
        assertThat(entradaA.getSubject().getId()).isEqualTo(entradaB.getSubject().getId());
        assertThat(entradaA.getProgram().getId()).isNotEqualTo(entradaB.getProgram().getId());
    }

    @Test
    void addTheSameSubjectTwoTimesToTheSameProgramFails() {
        Subject subject = persistSubject("Física I");
        Program program = persistProgram("Tecnicatura C");
        entityManager.flush();

        agregarMateria(program, subject, 1);

        assertThatThrownBy(() -> agregarMateria(program, subject, 1))
                .isInstanceOf(ProgramSubjectAlreadyExistsException.class);
    }

    @Test
    void tagWithGroupElectiveWithoutRangeAssignedFails() {
        Subject subject = persistSubject("Portugués I");
        Program program = persistProgram("Tecnicatura D");
        ElectiveGroup group = new ElectiveGroup();
        group.setName("Idiomas");
        entityManager.persist(group);
        entityManager.flush();

        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), 1, CurricularTerm.FIRST, 5, null, null, group.getId());

        assertThatThrownBy(() -> programSubjectService.agregarMateria(program.getId(), request))
                .isInstanceOf(ElectiveGroupNotAssignedException.class);

        electiveGroupService.asignarACarrera(program.getId(),
                new com.red_escuela.backend.dto.AssignElectiveGroupRequest(group.getId(), 1, 2));

        ProgramSubject created = programSubjectService.agregarMateria(program.getId(), request);
        assertThat(created.getElectiveGroup().getId()).isEqualTo(group.getId());
    }

    @Test
    void prerequisiteBetweenProgramsDifferentFails() {
        Program carreraA = persistProgram("Tecnicatura E");
        Program carreraB = persistProgram("Tecnicatura F");
        Subject subjectA = persistSubject("Álgebra");
        Subject subjectB = persistSubject("Geometría");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(carreraA, subjectA, 1);
        ProgramSubject entradaB = agregarMateria(carreraB, subjectB, 1);

        AddPrerequisiteRequest request = new AddPrerequisiteRequest(
                entradaB.getId(), RequirementType.APPROVED, null, null);

        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaA.getId(), request))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    @Test
    void prerequisiteThatCreatesACycleOfTwoStepsFails() {
        Program program = persistProgram("Tecnicatura G");
        Subject subjectA = persistSubject("Materia A");
        Subject subjectB = persistSubject("Materia B");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateriaSinAnio(program, subjectA);
        ProgramSubject entradaB = agregarMateriaSinAnio(program, subjectB);

        // A requiere B
        programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));

        // B requiere A -> cerraría el ciclo A->B->A
        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaB.getId(),
                new AddPrerequisiteRequest(entradaA.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(CircularPrerequisiteException.class);
    }

    @Test
    void prerequisiteThatCreatesACycleOfThreeStepsFails() {
        Program program = persistProgram("Tecnicatura H");
        Subject subjectA = persistSubject("Materia X");
        Subject subjectB = persistSubject("Materia Y");
        Subject subjectC = persistSubject("Materia Z");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateriaSinAnio(program, subjectA);
        ProgramSubject entradaB = agregarMateriaSinAnio(program, subjectB);
        ProgramSubject entradaC = agregarMateriaSinAnio(program, subjectC);

        // A requiere B, B requiere C
        programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));
        programSubjectService.agregarCorrelatividad(entradaB.getId(),
                new AddPrerequisiteRequest(entradaC.getId(), RequirementType.APPROVED, null, null));

        // C requiere A -> cerraría el ciclo A->B->C->A (3 pasos, no se corta en el primer nivel)
        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaC.getId(),
                new AddPrerequisiteRequest(entradaA.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(CircularPrerequisiteException.class);
    }

    @Test
    void addSubjectLeavesTheStatusInActive() {
        Subject subject = persistSubject("Materia con status");
        Program program = persistProgram("Tecnicatura I");
        entityManager.flush();

        ProgramSubject created = agregarMateria(program, subject, 1);

        assertThat(created.getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void removePrerequisiteNonexistentFails() {
        assertThatThrownBy(() -> programSubjectService.quitarCorrelatividad(999999L))
                .isInstanceOf(PrerequisiteNotFoundException.class);
    }

    private ProgramSubject agregarMateria(Program program, Subject subject, int year, CurricularTerm term) {
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), year, term, 10, null, null, null);
        return programSubjectService.agregarMateria(program.getId(), request);
    }

    // Pedido explícito del usuario: dos materias anuales del mismo año se cursan al
    // mismo tiempo, ninguna puede depender de la otra.
    @Test
    void prerequisiteBetweenTwoAnnualsOfTheSameYearFails() {
        Program program = persistProgram("Tecnicatura correlativas 1");
        Subject subjectA = persistSubject("Anual A");
        Subject subjectB = persistSubject("Anual B");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 1, CurricularTerm.ANNUAL);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1, CurricularTerm.ANNUAL);

        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    // Dos cuatrimestrales del mismo año y el mismo cuatrimestre también se solapan.
    @Test
    void prerequisiteBetweenTwoSubjectsOfTheSameYearAndTermFails() {
        Program program = persistProgram("Tecnicatura correlativas 2");
        Subject subjectA = persistSubject("1C A");
        Subject subjectB = persistSubject("1C B");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 1, CurricularTerm.FIRST);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    // Depender del cuatrimestre anterior del MISMO año sí es válido (2do cuatrimestre
    // puede requerir el 1ro: ya terminó de cursarse cuando arranca el que lo requiere).
    @Test
    void prerequisiteWithTheTermPreviousOfTheSameYearWorks() {
        Program program = persistProgram("Tecnicatura correlativas 3");
        Subject subjectA = persistSubject("2C A");
        Subject subjectB = persistSubject("1C previa");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 1, CurricularTerm.SECOND);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1, CurricularTerm.FIRST);

        var creada = programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));

        assertThat(creada.getPrerequisiteProgramSubject().getId()).isEqualTo(entradaB.getId());
    }

    // Depender del año anterior en el MISMO cuatrimestre también es válido (1er
    // cuatrimestre de 2do año puede requerir el 1er cuatrimestre de 1er año).
    @Test
    void prerequisiteWithTheYearPreviousSameTermWorks() {
        Program program = persistProgram("Tecnicatura correlativas 4");
        Subject subjectA = persistSubject("Año 2 1C");
        Subject subjectB = persistSubject("Año 1 1C");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 2, CurricularTerm.FIRST);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1, CurricularTerm.FIRST);

        var creada = programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));

        assertThat(creada.getPrerequisiteProgramSubject().getId()).isEqualTo(entradaB.getId());
    }

    // Una anual de 1er año todavía no terminó de cursarse cuando arranca el 2do
    // cuatrimestre de ESE MISMO año (se solapan) — tampoco es válida.
    @Test
    void prerequisiteBetweenAnnualAndSecondTermOfTheSameYearFails() {
        Program program = persistProgram("Tecnicatura correlativas 5");
        Subject subjectA = persistSubject("2C del año");
        Subject subjectB = persistSubject("Anual del año");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 1, CurricularTerm.SECOND);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1, CurricularTerm.ANNUAL);

        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    // Depender de una materia que se cursa DESPUÉS (2do cuatrimestre como correlativa
    // del 1ro, mismo año) tampoco es válido.
    @Test
    void prerequisiteWithASubjectSubsequentFails() {
        Program program = persistProgram("Tecnicatura correlativas 6");
        Subject subjectA = persistSubject("1C que no puede depender del 2C");
        Subject subjectB = persistSubject("2C posterior");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 1, CurricularTerm.FIRST);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1, CurricularTerm.SECOND);

        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }
}
