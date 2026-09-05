package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.exception.InvalidProgramCohortException;
import com.red_escuela.backend.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.ProgramCohortRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cohortes de admisión de una carrera (Épica 8 Fase 2) — política institucional:
 * exactamente 2 por año (ordenEnAnio 1 y 2, ver {@link ProgramCohort}). Solo el
 * Director las define (mismo criterio que el resto de precios/mora, Fase 1).
 */
@Service
public class ProgramCohortService {

    private final ProgramCohortRepository programCohortRepository;
    private final ProgramRepository programRepository;

    public ProgramCohortService(ProgramCohortRepository programCohortRepository, ProgramRepository programRepository) {
        this.programCohortRepository = programCohortRepository;
        this.programRepository = programRepository;
    }

    @Transactional
    public ProgramCohort create(CreateProgramCohortRequest request) {
        if (request.ordenEnAnio() != 1 && request.ordenEnAnio() != 2) {
            throw new InvalidProgramCohortException(
                    "ordenEnAnio tiene que ser 1 o 2 — una carrera tiene exactamente 2 cohortes por año");
        }
        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new ProgramNotFoundException(request.programId()));

        programCohortRepository.findByProgramIdAndOrdenEnAnio(program.getId(), request.ordenEnAnio())
                .ifPresent(existing -> {
                    throw new InvalidProgramCohortException(
                            "La carrera " + program.getId() + " ya tiene una cohorte con ordenEnAnio " + request.ordenEnAnio());
                });

        if (request.admissionStart() != null && request.admissionEnd() != null
                && request.admissionStart().isAfter(request.admissionEnd())) {
            throw new InvalidProgramCohortException(
                    "La ventana de admisión de la cohorte está mal configurada (admissionStart posterior a admissionEnd)");
        }

        ProgramCohort cohort = new ProgramCohort();
        cohort.setProgram(program);
        cohort.setLabel(request.label());
        cohort.setOrdenEnAnio(request.ordenEnAnio());
        cohort.setAdmissionStart(request.admissionStart());
        cohort.setAdmissionEnd(request.admissionEnd());
        cohort.setCursadaStart(request.cursadaStart());
        return programCohortRepository.save(cohort);
    }

    public List<ProgramCohort> listByProgram(Long programId) {
        return programCohortRepository.findByProgramIdOrderByOrdenEnAnio(programId);
    }

    ProgramCohort findOrThrow(Long id) {
        return programCohortRepository.findById(id)
                .orElseThrow(() -> new ProgramCohortNotFoundException(id));
    }
}
