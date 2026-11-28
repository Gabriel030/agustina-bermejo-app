package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.springframework.stereotype.Component;

@Component
public class ProgramSubjectReferenceFinder {

    private final AcademicReferenceFinder academicReferenceFinder;
    private final ProgramPlanSubjectGuard programPlanSubjectGuard;

    public ProgramSubjectReferenceFinder(AcademicReferenceFinder academicReferenceFinder,
                                         ProgramPlanSubjectGuard programPlanSubjectGuard) {
        this.academicReferenceFinder = academicReferenceFinder;
        this.programPlanSubjectGuard = programPlanSubjectGuard;
    }

    public Program findProgram(Long programId) {
        return academicReferenceFinder.findProgram(programId);
    }

    public Subject findSubjectInProgramPlan(ProgramEnrollment enrollment, Long subjectId) {
        Subject subject = academicReferenceFinder.findSubject(subjectId);
        programPlanSubjectGuard.requireSubjectInPlan(enrollment, subject.getId());
        return subject;
    }
}
