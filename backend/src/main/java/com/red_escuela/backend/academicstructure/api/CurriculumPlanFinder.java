package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.exception.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class CurriculumPlanFinder {

    private final CurriculumPlanRepository curriculumPlanRepository;
    private final Clock clock;

    public CurriculumPlanFinder(CurriculumPlanRepository curriculumPlanRepository, Clock clock) {
        this.curriculumPlanRepository = curriculumPlanRepository;
        this.clock = clock;
    }

    public Optional<CurriculumPlanView> findCurrentPlanOfProgram(Long programId) {
        return curriculumPlanRepository.findCurrentByProgramId(programId).map(CurriculumPlanFinder::toView);
    }

    public CurriculumPlanView findOrThrow(Long planId) {
        return toView(requirePlan(planId));
    }

    public void requireOpenForNewStudents(Long planId) {
        requirePlan(planId).requireOpenForNewStudents(LocalDate.now(clock));
    }

    public boolean allowsExistingStudentsToContinue(Long planId) {
        return requirePlan(planId).allowsExistingStudentsToContinue(LocalDate.now(clock));
    }

    private CurriculumPlan requirePlan(Long planId) {
        return curriculumPlanRepository.findById(planId).orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
    }

    private static CurriculumPlanView toView(CurriculumPlan plan) {
        return new CurriculumPlanView(plan.getId(), plan.getProgram().getId(), plan.getVersionNumber(), plan.getName(),
                plan.getStatus(), plan.getValidFrom(), plan.getValidUntil(), plan.getGraduationDeadline(),
                plan.getTotalYears(), plan.getTotalCreditsRequired(), plan.getTotalHoursRequired());
    }
}
