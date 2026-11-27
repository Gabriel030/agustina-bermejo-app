package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.exception.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class GetCurriculumPlanService implements GetCurriculumPlanUseCase {

    private final CurriculumPlanRepository curriculumPlanRepository;

    public GetCurriculumPlanService(CurriculumPlanRepository curriculumPlanRepository) {
        this.curriculumPlanRepository = curriculumPlanRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CurriculumPlanResponse get(Long planId) {
        return curriculumPlanRepository.findById(planId).map(CurriculumPlanResponse::from)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CurriculumPlanResponse> listByProgram(Long programId) {
        return curriculumPlanRepository.findByProgramIdOrderByVersionNumberAsc(programId).stream()
                .map(CurriculumPlanResponse::from).toList();
    }
}
