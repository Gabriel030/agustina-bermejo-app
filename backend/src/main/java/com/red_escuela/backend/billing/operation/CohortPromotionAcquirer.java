package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.springframework.stereotype.Service;

@Service
public class CohortPromotionAcquirer {
    private final CohortPromotionAcquisitionPort acquisitionPort;

    public CohortPromotionAcquirer(CohortPromotionAcquisitionPort acquisitionPort) {
        this.acquisitionPort = acquisitionPort;
    }

    public void acquireWhenEligible(ProgramEnrollment enrollment, PaymentPlanSelection selection) {
        Long cohortId = enrollment.getProgramCohort() == null ? null : enrollment.getProgramCohort().getId();
        acquisitionPort.acquireCohortPromotionWhenEligible(CohortPromotionAcquisitionRequest.forProgramEnrollment(enrollment.getId(), enrollment.getProgram().getId(),
                cohortId, enrollment.getEnrollmentDate().toLocalDate(), selection.getCreatedBy().getId()));
    }

    public void acquireWhenEligible(CourseEnrollment enrollment, PaymentPlanSelection selection) {
        acquisitionPort.acquireCohortPromotionWhenEligible(CohortPromotionAcquisitionRequest.forCourseEnrollment(enrollment.getId(), enrollment.getCourse().getId(),
                enrollment.getEnrollmentDate().toLocalDate(), selection.getCreatedBy().getId()));
    }
}
