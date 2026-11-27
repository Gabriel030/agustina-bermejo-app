package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.CurriculumPlanEnrollmentActivityPort;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.exception.CurriculumPlanLockedException;
import org.springframework.stereotype.Component;

@Component
public class CurriculumPlanEditPolicy {

    private final CurriculumPlanEnrollmentActivityPort enrollmentActivityPort;

    public CurriculumPlanEditPolicy(CurriculumPlanEnrollmentActivityPort enrollmentActivityPort) {
        this.enrollmentActivityPort = enrollmentActivityPort;
    }

    public void requireStructureEditable(CurriculumPlan plan) {
        if (plan.isDraft()) {
            return;
        }
        if (enrollmentActivityPort.hasEnrolledStudentsInProgram(plan.getProgram().getId())) {
            throw new CurriculumPlanLockedException(plan.getId());
        }
    }
}
