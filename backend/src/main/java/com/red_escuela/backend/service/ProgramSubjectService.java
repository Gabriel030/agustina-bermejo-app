package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.entity.ElectiveGroup;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.CircularPrerequisiteException;
import com.red_escuela.backend.exception.ElectiveGroupNotAssignedException;
import com.red_escuela.backend.exception.ElectiveGroupNotFoundException;
import com.red_escuela.backend.exception.InvalidPrerequisiteException;
import com.red_escuela.backend.exception.PrerequisiteAlreadyExistsException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.exception.ProgramSubjectAlreadyExistsException;
import com.red_escuela.backend.exception.PrerequisiteNotFoundException;
import com.red_escuela.backend.exception.ProgramSubjectNotFoundException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.repository.ElectiveGroupRepository;
import com.red_escuela.backend.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.repository.ProgramSubjectRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProgramSubjectService {

    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramRepository programRepository;
    private final SubjectRepository subjectRepository;
    private final ElectiveGroupRepository electiveGroupRepository;
    private final ProgramElectiveGroupRepository programElectiveGroupRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;

    public ProgramSubjectService(
            ProgramSubjectRepository programSubjectRepository,
            ProgramRepository programRepository,
            SubjectRepository subjectRepository,
            ElectiveGroupRepository electiveGroupRepository,
            ProgramElectiveGroupRepository programElectiveGroupRepository,
            ProgramSubjectPrerequisiteRepository prerequisiteRepository) {
        this.programSubjectRepository = programSubjectRepository;
        this.programRepository = programRepository;
        this.subjectRepository = subjectRepository;
        this.electiveGroupRepository = electiveGroupRepository;
        this.programElectiveGroupRepository = programElectiveGroupRepository;
        this.prerequisiteRepository = prerequisiteRepository;
    }

    @Transactional
    public ProgramSubject agregarMateria(Long programId, CreateProgramSubjectRequest request) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new SubjectNotFoundException(request.subjectId()));

        if (programSubjectRepository.findByProgramIdAndSubjectId(programId, request.subjectId()).isPresent()) {
            throw new ProgramSubjectAlreadyExistsException(programId, request.subjectId());
        }

        ElectiveGroup electiveGroup = null;
        if (request.electiveGroupId() != null) {
            electiveGroup = electiveGroupRepository.findById(request.electiveGroupId())
                    .orElseThrow(() -> new ElectiveGroupNotFoundException(request.electiveGroupId()));
            // Decisión 8: no se puede tagear una materia con un grupo que esa carrera
            // todavía no asignó (sin rango definido, quedaría una electiva huérfana).
            programElectiveGroupRepository.findByProgramIdAndElectiveGroupId(programId, request.electiveGroupId())
                    .orElseThrow(() -> new ElectiveGroupNotAssignedException(programId, request.electiveGroupId()));
        }

        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCurricularYear(request.curricularYear());
        programSubject.setCurricularTerm(request.curricularTerm());
        programSubject.setCredits(request.credits());
        programSubject.setSubjectRecurrence(request.subjectRecurrence());
        programSubject.setSortOrder(request.sortOrder());
        programSubject.setElectiveGroup(electiveGroup);
        programSubject.setStatus(StatusType.ACTIVE);

        try {
            return programSubjectRepository.saveAndFlush(programSubject);
        } catch (DataIntegrityViolationException e) {
            throw new ProgramSubjectAlreadyExistsException(programId, request.subjectId());
        }
    }

    @Transactional
    public ProgramSubject editarMateria(Long id, UpdateProgramSubjectRequest request) {
        ProgramSubject programSubject = findOrThrow(id);

        if (request.curricularYear() != null) {
            programSubject.setCurricularYear(request.curricularYear());
        }
        if (request.curricularTerm() != null) {
            programSubject.setCurricularTerm(request.curricularTerm());
        }
        if (request.credits() != null) {
            programSubject.setCredits(request.credits());
        }
        if (request.subjectRecurrence() != null) {
            programSubject.setSubjectRecurrence(request.subjectRecurrence());
        }
        if (request.sortOrder() != null) {
            programSubject.setSortOrder(request.sortOrder());
        }
        if (request.electiveGroupId() != null) {
            Long programId = programSubject.getProgram().getId();
            ElectiveGroup electiveGroup = electiveGroupRepository.findById(request.electiveGroupId())
                    .orElseThrow(() -> new ElectiveGroupNotFoundException(request.electiveGroupId()));
            programElectiveGroupRepository.findByProgramIdAndElectiveGroupId(programId, request.electiveGroupId())
                    .orElseThrow(() -> new ElectiveGroupNotAssignedException(programId, request.electiveGroupId()));
            programSubject.setElectiveGroup(electiveGroup);
        }

        return programSubjectRepository.save(programSubject);
    }

    public List<ProgramSubject> listarPorCarrera(Long programId, StatusType status) {
        return status == null
                ? programSubjectRepository.findByProgramId(programId)
                : programSubjectRepository.findByProgramIdAndStatus(programId, status);
    }

    public ProgramSubject verDetalle(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void desactivar(Long id) {
        ProgramSubject programSubject = findOrThrow(id);
        programSubject.setStatus(StatusType.INACTIVE);
        programSubjectRepository.save(programSubject);
    }

    @Transactional
    public void reactivar(Long id) {
        ProgramSubject programSubject = findOrThrow(id);
        programSubject.setStatus(StatusType.ACTIVE);
        programSubjectRepository.save(programSubject);
    }

    @Transactional
    public ProgramSubjectPrerequisite agregarCorrelatividad(Long programSubjectId, AddPrerequisiteRequest request) {
        ProgramSubject objetivo = findOrThrow(programSubjectId);
        ProgramSubject prerequisitePropuesto = findOrThrow(request.prerequisiteProgramSubjectId());

        if (objetivo.getId().equals(prerequisitePropuesto.getId())) {
            throw new InvalidPrerequisiteException("Una materia no puede ser prerrequisito de sí misma");
        }
        if (!objetivo.getProgram().getId().equals(prerequisitePropuesto.getProgram().getId())) {
            throw new InvalidPrerequisiteException(
                    "Las correlatividades solo pueden darse entre materias de la misma carrera");
        }
        if (prerequisiteRepository.existsByProgramSubjectIdAndPrerequisiteProgramSubjectIdAndRequirementType(
                objetivo.getId(), prerequisitePropuesto.getId(), request.requirementType())) {
            throw new PrerequisiteAlreadyExistsException(objetivo.getId(), prerequisitePropuesto.getId());
        }
        if (seCreariaUnCiclo(objetivo.getId(), prerequisitePropuesto.getId())) {
            throw new CircularPrerequisiteException(objetivo.getId(), prerequisitePropuesto.getId());
        }

        ProgramSubjectPrerequisite prerequisite = new ProgramSubjectPrerequisite();
        prerequisite.setProgramSubject(objetivo);
        prerequisite.setPrerequisiteProgramSubject(prerequisitePropuesto);
        prerequisite.setRequirementType(request.requirementType());
        prerequisite.setMinGrade(request.minGrade());
        prerequisite.setNotes(request.notes());

        try {
            return prerequisiteRepository.saveAndFlush(prerequisite);
        } catch (DataIntegrityViolationException e) {
            throw new PrerequisiteAlreadyExistsException(objetivo.getId(), prerequisitePropuesto.getId());
        }
    }

    /**
     * DFS sobre "quién requiere a quién": arranca en el prerrequisito propuesto y
     * recorre lo que ESE, a su vez, ya requiere (transitivamente). Si en algún punto
     * se llega de vuelta a la materia objetivo, agregar el arco objetivo->prerequisito
     * cerraría un ciclo — se rechaza antes de tocar la base (Spec Estructura académica,
     * sección "Servicios propuestos").
     */
    private boolean seCreariaUnCiclo(Long programSubjectObjetivoId, Long prerequisitePropuestoId) {
        Set<Long> visitados = new HashSet<>();
        Deque<Long> pila = new ArrayDeque<>();
        pila.push(prerequisitePropuestoId);

        while (!pila.isEmpty()) {
            Long actual = pila.pop();
            if (actual.equals(programSubjectObjetivoId)) {
                return true;
            }
            if (!visitados.add(actual)) {
                continue;
            }
            for (ProgramSubjectPrerequisite req : prerequisiteRepository.findByProgramSubjectId(actual)) {
                pila.push(req.getPrerequisiteProgramSubject().getId());
            }
        }
        return false;
    }

    public List<ProgramSubjectPrerequisite> listarCorrelatividades(Long programSubjectId) {
        return prerequisiteRepository.findByProgramSubjectId(programSubjectId);
    }

    @Transactional
    public void quitarCorrelatividad(Long prerequisiteId) {
        // deleteById no tira excepción si el id no existe (findById().ifPresent(...) por
        // dentro) — sin este chequeo, borrar un id inexistente devolvería 204 igual.
        ProgramSubjectPrerequisite prerequisite = prerequisiteRepository.findById(prerequisiteId)
                .orElseThrow(() -> new PrerequisiteNotFoundException(prerequisiteId));
        prerequisiteRepository.delete(prerequisite);
    }

    private ProgramSubject findOrThrow(Long id) {
        return programSubjectRepository.findById(id)
                .orElseThrow(() -> new ProgramSubjectNotFoundException(id));
    }
}
