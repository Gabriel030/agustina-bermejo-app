package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class ProgramCohortFinder {

    private final ProgramCohortRepository programCohortRepository;

    public ProgramCohortFinder(ProgramCohortRepository programCohortRepository) {
        this.programCohortRepository = programCohortRepository;
    }

    public ProgramCohort findOrThrow(Long id) {
        return programCohortRepository.findById(id).orElseThrow(() -> new ProgramCohortNotFoundException(id));
    }

    public Optional<ProgramCohort> findById(Long id) {
        return programCohortRepository.findById(id);
    }

    public List<ProgramCohort> findByTemplateId(Long templateId) {
        return programCohortRepository.findByTemplateId(templateId);
    }

    public List<ProgramCohort> findByTemplateIds(Collection<Long> templateIds) {
        return programCohortRepository.findByTemplateIdIn(templateIds);
    }

    public Optional<ProgramCohort> findByTemplateIdAndIntake(Long templateId, CohortIntake intake) {
        return programCohortRepository.findByTemplateIdAndIntake(templateId, intake);
    }

    public boolean isWithinAdmissionWindow(ProgramCohort cohort, LocalDate at) {
        return !at.isBefore(cohort.getAdmissionStart()) && !at.isAfter(cohort.getAdmissionEnd());
    }
}
