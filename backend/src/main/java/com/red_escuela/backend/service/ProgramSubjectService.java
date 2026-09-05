package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.entity.CurricularTerm;
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
    public ProgramSubject addSubject(Long programId, CreateProgramSubjectRequest request) {
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
    public ProgramSubject updateSubject(Long id, UpdateProgramSubjectRequest request) {
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

    public List<ProgramSubject> listByProgram(Long programId, StatusType status) {
        return status == null
                ? programSubjectRepository.findByProgramId(programId)
                : programSubjectRepository.findByProgramIdAndStatus(programId, status);
    }

    public ProgramSubject viewDetail(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        ProgramSubject programSubject = findOrThrow(id);
        programSubject.setStatus(StatusType.INACTIVE);
        programSubjectRepository.save(programSubject);
    }

    @Transactional
    public void reactivate(Long id) {
        ProgramSubject programSubject = findOrThrow(id);
        programSubject.setStatus(StatusType.ACTIVE);
        programSubjectRepository.save(programSubject);
    }

    @Transactional
    public ProgramSubjectPrerequisite addPrerequisite(Long programSubjectId, AddPrerequisiteRequest request) {
        ProgramSubject objetivo = findOrThrow(programSubjectId);
        ProgramSubject prerequisitePropuesto = findOrThrow(request.prerequisiteProgramSubjectId());

        if (objetivo.getId().equals(prerequisitePropuesto.getId())) {
            throw new InvalidPrerequisiteException("Una materia no puede ser prerrequisito de sí misma");
        }
        if (!objetivo.getProgram().getId().equals(prerequisitePropuesto.getProgram().getId())) {
            throw new InvalidPrerequisiteException(
                    "Las correlatividades solo pueden darse entre materias de la misma carrera");
        }
        validateChronologicalOrder(objetivo, prerequisitePropuesto);
        if (prerequisiteRepository.existsByProgramSubjectIdAndPrerequisiteProgramSubjectIdAndRequirementType(
                objetivo.getId(), prerequisitePropuesto.getId(), request.requirementType())) {
            throw new PrerequisiteAlreadyExistsException(objetivo.getId(), prerequisitePropuesto.getId());
        }
        if (wouldCreateCycle(objetivo.getId(), prerequisitePropuesto.getId())) {
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
     * Una correlativa tiene que haber terminado de cursarse antes de que empiece la
     * materia que la requiere — no alcanza con "año anterior o igual", hace falta que
     * los períodos de cursada no se toquen. Pedido explícito del usuario: una materia
     * anual de 1er año no puede ser correlativa de otra anual de 1er año (se cursan al
     * mismo tiempo), pero sí puede depender del cuatrimestre anterior del mismo año, o
     * del año anterior en el mismo cuatrimestre.
     * <p>
     * Se modela cada (año, cuatrimestre) como un rango de "medios años" dentro del plan
     * (1er cuatrimestre de año Y ocupa un solo medio-año, 2do cuatrimestre el siguiente,
     * anual ocupa los dos medios-años de ese Y) y se exige que la correlativa termine
     * estrictamente antes de que arranque la materia objetivo — eso además cubre, sin
     * necesidad de un caso aparte, combinaciones que el usuario no mencionó explícitamente
     * pero tienen el mismo problema de fondo (ej. 1er cuatrimestre correlativa de la
     * anual del mismo año: se solapan igual, aunque no compartan el mismo "tipo").
     * Si a alguna de las dos materias le falta año/cuatrimestre en el plan, no hay con
     * qué validar — se deja pasar (esa incompletitud no es responsabilidad de esta
     * validación).
     */
    private void validateChronologicalOrder(ProgramSubject objetivo, ProgramSubject prerequisito) {
        if (objetivo.getCurricularYear() == null || objetivo.getCurricularTerm() == null
                || prerequisito.getCurricularYear() == null || prerequisito.getCurricularTerm() == null) {
            return;
        }
        if (finCronologico(prerequisito) >= chronologicalStart(objetivo)) {
            throw new InvalidPrerequisiteException(
                    "\"" + prerequisito.getSubject().getName() + "\" no puede ser correlativa de \""
                            + objetivo.getSubject().getName()
                            + "\": tiene que cursarse y terminar antes de que empiece, no puede compartir "
                            + "cuatrimestre ni año de cursada con ella");
        }
    }

    private int chronologicalStart(ProgramSubject ps) {
        return (ps.getCurricularYear() - 1) * 2 + (ps.getCurricularTerm() == CurricularTerm.SECOND ? 2 : 1);
    }

    private int finCronologico(ProgramSubject ps) {
        return (ps.getCurricularYear() - 1) * 2 + (ps.getCurricularTerm() == CurricularTerm.FIRST ? 1 : 2);
    }

    /**
     * DFS sobre "quién requiere a quién": arranca en el prerrequisito propuesto y
     * recorre lo que ESE, a su vez, ya requiere (transitivamente). Si en algún punto
     * se llega de vuelta a la materia objetivo, agregar el arco objetivo->prerequisito
     * cerraría un ciclo — se rechaza antes de tocar la base (Spec Estructura académica,
     * sección "Servicios propuestos").
     */
    private boolean wouldCreateCycle(Long targetProgramSubjectId, Long proposedPrerequisiteId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(proposedPrerequisiteId);

        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (current.equals(targetProgramSubjectId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            for (ProgramSubjectPrerequisite req : prerequisiteRepository.findByProgramSubjectId(current)) {
                stack.push(req.getPrerequisiteProgramSubject().getId());
            }
        }
        return false;
    }

    public List<ProgramSubjectPrerequisite> listPrerequisites(Long programSubjectId) {
        return prerequisiteRepository.findByProgramSubjectId(programSubjectId);
    }

    @Transactional
    public void removePrerequisite(Long prerequisiteId) {
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
