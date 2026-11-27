package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.exception.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ActivateCurriculumPlanService implements ActivateCurriculumPlanUseCase {

    private final CurriculumPlanRepository curriculumPlanRepository;

    public ActivateCurriculumPlanService(CurriculumPlanRepository curriculumPlanRepository) {
        this.curriculumPlanRepository = curriculumPlanRepository;
    }

    @Override
    @Transactional
    public CurriculumPlanResponse activate(Long planId) {
        CurriculumPlan plan = curriculumPlanRepository.findById(planId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
        plan.activate();
        curriculumPlanRepository.save(plan);
        curriculumPlanRepository.findCurrentByProgramId(plan.getProgram().getId())
                .filter(current -> current.getId().equals(plan.getId()))
                .ifPresent(current -> plan.getProgram().adoptCurriculumTotals(plan));
        return CurriculumPlanResponse.from(plan);
    }
}
