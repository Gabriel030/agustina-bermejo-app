package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.AssignElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupInUseException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InvalidElectiveRangeException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ElectiveGroupService {

    private final ElectiveGroupRepository electiveGroupRepository;
    private final ProgramElectiveGroupRepository programElectiveGroupRepository;
    private final ProgramRepository programRepository;
    private final ProgramSubjectRepository programSubjectRepository;

    public ElectiveGroupService(
            ElectiveGroupRepository electiveGroupRepository,
            ProgramElectiveGroupRepository programElectiveGroupRepository,
            ProgramRepository programRepository,
            ProgramSubjectRepository programSubjectRepository) {
        this.electiveGroupRepository = electiveGroupRepository;
        this.programElectiveGroupRepository = programElectiveGroupRepository;
        this.programRepository = programRepository;
        this.programSubjectRepository = programSubjectRepository;
    }

    @Transactional
    public ElectiveGroup crearGrupo(CreateElectiveGroupRequest request) {
        ElectiveGroup group = new ElectiveGroup();
        group.setName(request.name());
        group.setDescription(request.description());
        try {
            return electiveGroupRepository.saveAndFlush(group);
        } catch (DataIntegrityViolationException e) {
            throw new ElectiveGroupAlreadyExistsException(request.name());
        }
    }

    @Transactional
    public ElectiveGroup editarGrupo(Long id, UpdateElectiveGroupRequest request) {
        ElectiveGroup group = electiveGroupRepository.findById(id)
                .orElseThrow(() -> new ElectiveGroupNotFoundException(id));
        if (request.name() != null) {
            group.setName(request.name());
        }
        if (request.description() != null) {
            group.setDescription(request.description());
        }
        return electiveGroupRepository.save(group);
    }

    public List<ElectiveGroup> listar() {
        return electiveGroupRepository.findAll();
    }

    public ElectiveGroup verDetalle(Long id) {
        return electiveGroupRepository.findById(id)
                .orElseThrow(() -> new ElectiveGroupNotFoundException(id));
    }

    public List<ProgramElectiveGroup> listarPorCarrera(Long programId) {
        return programElectiveGroupRepository.findByProgramId(programId);
    }

    @Transactional
    public ProgramElectiveGroup asignarACarrera(Long programId, AssignElectiveGroupRequest request) {
        if (request.minRequired() > request.maxAllowed()) {
            throw new InvalidElectiveRangeException(request.minRequired(), request.maxAllowed());
        }

        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));
        ElectiveGroup group = electiveGroupRepository.findById(request.electiveGroupId())
                .orElseThrow(() -> new ElectiveGroupNotFoundException(request.electiveGroupId()));

        ProgramElectiveGroup assignment = programElectiveGroupRepository
                .findByProgramIdAndElectiveGroupId(programId, request.electiveGroupId())
                .orElseGet(ProgramElectiveGroup::new);
        assignment.setProgram(program);
        assignment.setElectiveGroup(group);
        assignment.setMinRequired(request.minRequired());
        assignment.setMaxAllowed(request.maxAllowed());
        return programElectiveGroupRepository.save(assignment);
    }

    @Transactional
    public void quitarDeCarrera(Long programId, Long electiveGroupId) {
        ProgramElectiveGroup assignment = programElectiveGroupRepository
                .findByProgramIdAndElectiveGroupId(programId, electiveGroupId)
                .orElseThrow(() -> new ElectiveGroupNotFoundException(electiveGroupId));

        boolean stillUsed = !programSubjectRepository
                .findByProgramIdAndElectiveGroupId(programId, electiveGroupId).isEmpty();
        if (stillUsed) {
            throw new ElectiveGroupInUseException(programId, electiveGroupId);
        }

        programElectiveGroupRepository.delete(assignment);
    }
}
