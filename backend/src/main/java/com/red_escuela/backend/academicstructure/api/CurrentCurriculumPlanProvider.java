package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class CurrentCurriculumPlanProvider {

    private final CurriculumPlanRepository curriculumPlanRepository;
    private final ProgramRepository programRepository;
    private final Clock clock;

    public CurrentCurriculumPlanProvider(CurriculumPlanRepository curriculumPlanRepository,
                                         ProgramRepository programRepository, Clock clock) {
        this.curriculumPlanRepository = curriculumPlanRepository;
        this.programRepository = programRepository;
        this.clock = clock;
    }

    public CurriculumPlan currentPlanOf(Program program) {
        return curriculumPlanRepository.findCurrentByProgramId(program.getId())
                .orElseGet(() -> curriculumPlanRepository.save(CurriculumPlan.initialVersionOf(program, LocalDate.now(clock))));
    }

    public CurriculumPlan currentPlanOfProgramId(Long programId) {
        return currentPlanOf(programRepository.getReferenceById(programId));
    }

    public CurriculumPlan createInitialPlanOf(Program program) {
        return curriculumPlanRepository.save(CurriculumPlan.initialVersionOf(program, LocalDate.now(clock)));
    }
}
