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
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private ProgramSubject agregarMateria(Program program, Subject subject, int year) {
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), year, CurricularTerm.FIRST, 10, null, null, null);
        return programSubjectService.agregarMateria(program.getId(), request);
    }

    @Test
    void mismoSubjectEnDosCarrerasCreaDosEntradasIndependientes() {
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
    void agregarElMismoSubjectDosVecesALaMismaCarreraFalla() {
        Subject subject = persistSubject("Física I");
        Program program = persistProgram("Tecnicatura C");
        entityManager.flush();

        agregarMateria(program, subject, 1);

        assertThatThrownBy(() -> agregarMateria(program, subject, 1))
                .isInstanceOf(ProgramSubjectAlreadyExistsException.class);
    }

    @Test
    void tagearConGrupoElectivoSinRangoAsignadoFalla() {
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
    void correlatividadEntreCarrerasDistintasFalla() {
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
    void correlatividadQueCreaUnCicloDeDosPasosFalla() {
        Program program = persistProgram("Tecnicatura G");
        Subject subjectA = persistSubject("Materia A");
        Subject subjectB = persistSubject("Materia B");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 1);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1);

        // A requiere B
        programSubjectService.agregarCorrelatividad(entradaA.getId(),
                new AddPrerequisiteRequest(entradaB.getId(), RequirementType.APPROVED, null, null));

        // B requiere A -> cerraría el ciclo A->B->A
        assertThatThrownBy(() -> programSubjectService.agregarCorrelatividad(entradaB.getId(),
                new AddPrerequisiteRequest(entradaA.getId(), RequirementType.APPROVED, null, null)))
                .isInstanceOf(CircularPrerequisiteException.class);
    }

    @Test
    void correlatividadQueCreaUnCicloDeTresPasosFalla() {
        Program program = persistProgram("Tecnicatura H");
        Subject subjectA = persistSubject("Materia X");
        Subject subjectB = persistSubject("Materia Y");
        Subject subjectC = persistSubject("Materia Z");
        entityManager.flush();

        ProgramSubject entradaA = agregarMateria(program, subjectA, 1);
        ProgramSubject entradaB = agregarMateria(program, subjectB, 1);
        ProgramSubject entradaC = agregarMateria(program, subjectC, 1);

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
    void agregarMateriaDejaElStatusEnActive() {
        Subject subject = persistSubject("Materia con status");
        Program program = persistProgram("Tecnicatura I");
        entityManager.flush();

        ProgramSubject created = agregarMateria(program, subject, 1);

        assertThat(created.getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void quitarCorrelatividadInexistenteFalla() {
        assertThatThrownBy(() -> programSubjectService.quitarCorrelatividad(999999L))
                .isInstanceOf(PrerequisiteNotFoundException.class);
    }
}
