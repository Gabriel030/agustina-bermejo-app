package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.SubjectNotInEnrollmentPlanException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import org.springframework.stereotype.Component;

@Component
public class ProgramPlanSubjectGuard {

    private final ProgramCurriculumFinder programCurriculumFinder;

    public ProgramPlanSubjectGuard(ProgramCurriculumFinder programCurriculumFinder) {
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public void requireSubjectInPlan(ProgramEnrollment enrollment, Long subjectId) {
        if (!programCurriculumFinder.subjectBelongsToPlan(enrollment.getPlan().getId(), subjectId)) {
            throw new SubjectNotInProgramPlanException(subjectId, enrollment.getProgram().getId());
        }
    }

    public void requireSubjectInEnrollmentPlan(ProgramEnrollment enrollment, Long subjectId) {
        Long planId = enrollment.getPlan().getId();
        if (!programCurriculumFinder.subjectIsActiveInPlan(planId, subjectId)) {
            throw new SubjectNotInEnrollmentPlanException(subjectId, planId);
        }
    }
}
