package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.SectionAbsenceLimitPort;
import com.red_escuela.backend.enrollment.dto.FinalExamEligibilityResponse;
import com.red_escuela.backend.enrollment.dto.FinalExamIneligibilityReason;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.springframework.stereotype.Component;

@Component
public class FinalExamEligibilityResolver {

    private final SectionAbsenceLimitPort sectionAbsenceLimitPort;

    public FinalExamEligibilityResolver(SectionAbsenceLimitPort sectionAbsenceLimitPort) {
        this.sectionAbsenceLimitPort = sectionAbsenceLimitPort;
    }

    public FinalExamEligibilityResponse resolve(SubjectSectionEnrollment enrollment) {
        if (enrollment.getStatus() != EnrollmentStatus.REGULAR) {
            return FinalExamEligibilityResponse.blockedBy(FinalExamIneligibilityReason.COURSEWORK_NOT_REGULAR);
        }
        if (sectionAbsenceLimitPort.exceedsAbsenceLimit(enrollment.getId())) {
            return FinalExamEligibilityResponse.blockedBy(FinalExamIneligibilityReason.FREE_BY_ABSENCES);
        }
        return FinalExamEligibilityResponse.allowed();
    }
}
