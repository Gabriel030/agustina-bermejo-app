package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.academicstructure.dto.AddCorequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.exception.CircularPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.CorequisiteAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.CorequisiteNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotAssignedException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InvalidCorequisiteException;
import com.red_escuela.backend.academicstructure.exception.InvalidPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.InvalidProgramSubjectCategoryException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectCorequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
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
    private final ProgramSubjectCorequisiteRepository corequisiteRepository;

    public ProgramSubjectService(
            ProgramSubjectRepository programSubjectRepository,
            ProgramRepository programRepository,
            SubjectRepository subjectRepository,
            ElectiveGroupRepository electiveGroupRepository,
            ProgramElectiveGroupRepository programElectiveGroupRepository,
            ProgramSubjectPrerequisiteRepository prerequisiteRepository,
            ProgramSubjectCorequisiteRepository corequisiteRepository) {
        this.programSubjectRepository = programSubjectRepository;
        this.programRepository = programRepository;
        this.subjectRepository = subjectRepository;
        this.electiveGroupRepository = electiveGroupRepository;
        this.programElectiveGroupRepository = programElectiveGroupRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.corequisiteRepository = corequisiteRepository;
    }

    @Transactional
    public ProgramSubject addSubject(Long programId, CreateProgramSubjectRequest request) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new SubjectNotFoundException(request.subjectId()));
        subject.requireAssignableToPlan();

        if (programSubjectRepository.findByProgramIdAndSubjectId(programId, request.subjectId()).isPresent()) {
            throw new ProgramSubjectAlreadyExistsException(programId, request.subjectId());
        }

        ElectiveGroup electiveGroup = null;
        if (request.electiveGroupId() != null) {
            electiveGroup = electiveGroupRepository.findById(request.electiveGroupId())
                    .orElseThrow(() -> new ElectiveGroupNotFoundException(request.electiveGroupId()));
            programElectiveGroupRepository.findByProgramIdAndElectiveGroupId(programId, request.electiveGroupId())
                    .orElseThrow(() -> new ElectiveGroupNotAssignedException(programId, request.electiveGroupId()));
        }

        validateCategoryInvariants(request.category(), request.curricularYear(), request.curricularTerm(), request.electiveGroupId());
        program.requireCurricularYearWithinDuration(request.curricularYear());

        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCategory(request.category());
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
    public ProgramSubject update(Long id, UpdateProgramSubjectRequest request) {
        ProgramSubject programSubject = findOrThrow(id);

        if (request.curricularYear() != null) {
            programSubject.getProgram().requireCurricularYearWithinDuration(request.curricularYear());
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

    public ProgramSubject get(Long id) {
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
        if (seCreariaUnCiclo(objetivo.getId(), prerequisitePropuesto.getId())) {
            throw new CircularPrerequisiteException(objetivo.getId(), prerequisitePropuesto.getId());
        }

        ProgramSubjectPrerequisite prerequisite = new ProgramSubjectPrerequisite();
        prerequisite.setProgramSubject(objetivo);
        prerequisite.setPrerequisiteProgramSubject(prerequisitePropuesto);
        prerequisite.setRequirementType(request.requirementType());
        prerequisite.setMinGrade(Grade.ofNullable(request.minGrade()));
        prerequisite.setNotes(request.notes());

        try {
            return prerequisiteRepository.saveAndFlush(prerequisite);
        } catch (DataIntegrityViolationException e) {
            throw new PrerequisiteAlreadyExistsException(objetivo.getId(), prerequisitePropuesto.getId());
        }
    }

    private void validateChronologicalOrder(ProgramSubject objetivo, ProgramSubject prerequisito) {
        if (objetivo.getCurricularYear() == null || objetivo.getCurricularTerm() == null
                || prerequisito.getCurricularYear() == null || prerequisito.getCurricularTerm() == null) {
            return;
        }
        if (finCronologico(prerequisito) >= inicioCronologico(objetivo)) {
            throw new InvalidPrerequisiteException(
                    "\"" + prerequisito.getSubject().getName() + "\" no puede ser correlativa de \""
                            + objetivo.getSubject().getName()
                            + "\": tiene que cursarse y terminar antes de que empiece, no puede compartir "
                            + "cuatrimestre ni año de cursada con ella");
        }
    }

    private int inicioCronologico(ProgramSubject ps) {
        return (ps.getCurricularYear() - 1) * 2 + (ps.getCurricularTerm() == CurricularTerm.SECOND ? 2 : 1);
    }

    private int finCronologico(ProgramSubject ps) {
        return (ps.getCurricularYear() - 1) * 2 + (ps.getCurricularTerm() == CurricularTerm.FIRST ? 1 : 2);
    }

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

    public List<ProgramSubjectPrerequisite> listPrerequisites(Long programSubjectId) {
        return prerequisiteRepository.findByProgramSubjectId(programSubjectId);
    }

    @Transactional
    public void removePrerequisite(Long prerequisiteId) {
        ProgramSubjectPrerequisite prerequisite = prerequisiteRepository.findById(prerequisiteId)
                .orElseThrow(() -> new PrerequisiteNotFoundException(prerequisiteId));
        prerequisiteRepository.delete(prerequisite);
    }

    private void validateCategoryInvariants(ProgramSubjectCategory category,
            Integer curricularYear, CurricularTerm curricularTerm, Long electiveGroupId) {
        switch (category) {
            case MANDATORY -> {
                if (curricularYear == null || curricularTerm == null) {
                    throw new InvalidProgramSubjectCategoryException(
                            "MANDATORY requires curricularYear and curricularTerm");
                }
            }
            case ELECTIVE -> {
                if (electiveGroupId == null) {
                    throw new InvalidProgramSubjectCategoryException(
                            "ELECTIVE requires electiveGroupId");
                }
            }
            case ADDITIONAL_MANDATORY -> {
                if (curricularYear != null || curricularTerm != null) {
                    throw new InvalidProgramSubjectCategoryException(
                            "ADDITIONAL_MANDATORY must not have curricularYear or curricularTerm");
                }
            }
        }
    }

    @Transactional
    public ProgramSubjectCorequisite addCorequisite(Long programSubjectId, AddCorequisiteRequest request) {
        ProgramSubject subject = findOrThrow(programSubjectId);
        ProgramSubject corequisite = findOrThrow(request.corequisiteProgramSubjectId());

        if (subject.getId().equals(corequisite.getId())) {
            throw new InvalidCorequisiteException("Una materia no puede ser co-requisito de sí misma");
        }
        if (!subject.getProgram().getId().equals(corequisite.getProgram().getId())) {
            throw new InvalidCorequisiteException(
                    "Los co-requisitos solo pueden darse entre materias de la misma carrera");
        }
        if (corequisiteRepository.existsByProgramSubjectIdAndCorequisiteProgramSubjectId(
                subject.getId(), corequisite.getId())) {
            throw new CorequisiteAlreadyExistsException(subject.getId(), corequisite.getId());
        }

        ProgramSubjectCorequisite link = new ProgramSubjectCorequisite();
        link.setProgramSubject(subject);
        link.setCorequisiteProgramSubject(corequisite);

        try {
            return corequisiteRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException e) {
            throw new CorequisiteAlreadyExistsException(subject.getId(), corequisite.getId());
        }
    }

    public List<ProgramSubjectCorequisite> listCorequisites(Long programSubjectId) {
        return corequisiteRepository.findByProgramSubjectId(programSubjectId);
    }

    @Transactional
    public void removeCorequisite(Long corequisiteId) {
        ProgramSubjectCorequisite link = corequisiteRepository.findById(corequisiteId)
                .orElseThrow(() -> new CorequisiteNotFoundException(corequisiteId));
        corequisiteRepository.delete(link);
    }

    private ProgramSubject findOrThrow(Long id) {
        return programSubjectRepository.findById(id)
                .orElseThrow(() -> new ProgramSubjectNotFoundException(id));
    }
}
