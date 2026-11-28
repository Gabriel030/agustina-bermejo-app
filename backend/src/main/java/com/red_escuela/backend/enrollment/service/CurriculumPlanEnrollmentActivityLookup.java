package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CurriculumPlanEnrollmentActivityPort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CurriculumPlanEnrollmentActivityLookup implements CurriculumPlanEnrollmentActivityPort {

    private static final List<ProgramEnrollmentStatus> STATUSES_BOUND_TO_PLAN = List.of(
            ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED, ProgramEnrollmentStatus.COMPLETED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public CurriculumPlanEnrollmentActivityLookup(ProgramEnrollmentRepository programEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    @Override
    public boolean hasEnrolledStudentsInPlan(Long planId) {
        return programEnrollmentRepository.existsByPlanIdAndStatusIn(planId, STATUSES_BOUND_TO_PLAN);
    }
}
