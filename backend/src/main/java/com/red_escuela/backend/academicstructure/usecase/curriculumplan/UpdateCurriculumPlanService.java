package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.exception.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanEditPolicy;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@UseCase
public class UpdateCurriculumPlanService implements UpdateCurriculumPlanUseCase {

    private final CurriculumPlanRepository curriculumPlanRepository;
    private final CurriculumPlanEditPolicy editPolicy;

    public UpdateCurriculumPlanService(CurriculumPlanRepository curriculumPlanRepository, CurriculumPlanEditPolicy editPolicy) {
        this.curriculumPlanRepository = curriculumPlanRepository;
        this.editPolicy = editPolicy;
    }

    @Override
    @Transactional
    public CurriculumPlanResponse update(Long planId, UpdateCurriculumPlanRequest request) {
        CurriculumPlan plan = curriculumPlanRepository.findById(planId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
        if (request.changesTotals()) {
            editPolicy.requireStructureEditable(plan);
            plan.changeTotals(request.totalYears(), request.totalCreditsRequired(), request.totalHoursRequired());
            curriculumPlanRepository.findCurrentByProgramId(plan.getProgram().getId())
                    .filter(current -> current.getId().equals(plan.getId()))
                    .ifPresent(current -> plan.getProgram().adoptCurriculumTotals(plan));
        }
        Optional.ofNullable(request.name()).ifPresent(plan::setName);
        plan.reschedule(request.validFrom(), request.graduationDeadline());
        return CurriculumPlanResponse.from(curriculumPlanRepository.save(plan));
    }
}
