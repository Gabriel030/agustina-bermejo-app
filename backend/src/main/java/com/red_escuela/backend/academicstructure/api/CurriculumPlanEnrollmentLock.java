package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class CurriculumPlanEnrollmentLock {

    private final CurriculumPlanRepository curriculumPlanRepository;
    private final Clock clock;

    public CurriculumPlanEnrollmentLock(CurriculumPlanRepository curriculumPlanRepository, Clock clock) {
        this.curriculumPlanRepository = curriculumPlanRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CurriculumPlan lockOpenForNewStudents(Long planId) {
        CurriculumPlan plan = curriculumPlanRepository.findByIdForShare(planId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
        plan.requireOpenForNewStudents(LocalDate.now(clock));
        return plan;
    }
}
