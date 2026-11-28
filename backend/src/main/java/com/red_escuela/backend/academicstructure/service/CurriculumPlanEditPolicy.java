package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.CurriculumPlanEnrollmentActivityPort;
import com.red_escuela.backend.academicstructure.domain.CurriculumPlanLockedException;
import com.red_escuela.backend.academicstructure.domain.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import org.springframework.stereotype.Component;

@Component
public class CurriculumPlanEditPolicy {

    private final CurriculumPlanEnrollmentActivityPort enrollmentActivityPort;
    private final CurriculumPlanRepository curriculumPlanRepository;

    public CurriculumPlanEditPolicy(CurriculumPlanEnrollmentActivityPort enrollmentActivityPort,
                                    CurriculumPlanRepository curriculumPlanRepository) {
        this.enrollmentActivityPort = enrollmentActivityPort;
        this.curriculumPlanRepository = curriculumPlanRepository;
    }

    public CurriculumPlan lockEditableStructureOf(Long planId) {
        CurriculumPlan plan = curriculumPlanRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
        if (!plan.isDraft() && enrollmentActivityPort.hasEnrolledStudentsInPlan(plan.getId())) {
            throw new CurriculumPlanLockedException(plan.getId());
        }
        return plan;
    }
}
