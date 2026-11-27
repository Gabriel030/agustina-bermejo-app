package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.ExpireCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.exception.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ExpireCurriculumPlanService implements ExpireCurriculumPlanUseCase {

    private final CurriculumPlanRepository curriculumPlanRepository;

    public ExpireCurriculumPlanService(CurriculumPlanRepository curriculumPlanRepository) {
        this.curriculumPlanRepository = curriculumPlanRepository;
    }

    @Override
    @Transactional
    public CurriculumPlanResponse expire(Long planId, ExpireCurriculumPlanRequest request) {
        CurriculumPlan plan = curriculumPlanRepository.findById(planId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
        plan.expire(request.expiresOn(), request.graduationDeadline());
        return CurriculumPlanResponse.from(curriculumPlanRepository.save(plan));
    }
}
