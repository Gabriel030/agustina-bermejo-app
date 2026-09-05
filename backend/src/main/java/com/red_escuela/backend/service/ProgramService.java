package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateProgramRequest;
import com.red_escuela.backend.dto.UpdateProgramRequest;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;

    public ProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    @Transactional
    public Program createProgram(CreateProgramRequest request) {
        Program program = new Program();
        program.setName(request.name());
        program.setTotalYears(request.totalYears());
        program.setPrice(request.price());
        program.setTotalCreditsRequired(request.totalCreditsRequired());
        program.setInstallmentsPerAcademicYear(request.installmentsPerAcademicYear());
        program.setStatus(StatusType.ACTIVE);
        return programRepository.save(program);
    }

    @Transactional
    public Program updateProgram(Long id, UpdateProgramRequest request) {
        Program program = findOrThrow(id);
        if (request.name() != null) {
            program.setName(request.name());
        }
        if (request.totalYears() != null) {
            program.setTotalYears(request.totalYears());
        }
        if (request.price() != null) {
            program.setPrice(request.price());
        }
        if (request.totalCreditsRequired() != null) {
            program.setTotalCreditsRequired(request.totalCreditsRequired());
        }
        if (request.installmentsPerAcademicYear() != null) {
            program.setInstallmentsPerAcademicYear(request.installmentsPerAcademicYear());
        }
        return programRepository.save(program);
    }

    public List<Program> list(StatusType status) {
        return status == null ? programRepository.findAll() : programRepository.findByStatus(status);
    }

    public Program viewDetail(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Program program = findOrThrow(id);
        program.setStatus(StatusType.INACTIVE);
        programRepository.save(program);
    }

    @Transactional
    public void reactivate(Long id) {
        Program program = findOrThrow(id);
        program.setStatus(StatusType.ACTIVE);
        programRepository.save(program);
    }

    private Program findOrThrow(Long id) {
        return programRepository.findById(id).orElseThrow(() -> new ProgramNotFoundException(id));
    }
}
