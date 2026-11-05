package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import org.springframework.stereotype.Component;

@Component
public class ProgramPlanSubjectGuard {

    private final ProgramCurriculumFinder programCurriculumFinder;

    public ProgramPlanSubjectGuard(ProgramCurriculumFinder programCurriculumFinder) {
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public void requireSubjectInPlan(Long programId, Long subjectId) {
        if (!programCurriculumFinder.subjectBelongsToProgram(programId, subjectId)) {
            throw new SubjectNotInProgramPlanException(subjectId, programId);
        }
    }
}
