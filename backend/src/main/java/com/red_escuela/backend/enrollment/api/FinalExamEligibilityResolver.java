package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.springframework.stereotype.Component;

import java.util.function.BooleanSupplier;

@Component
public class FinalExamEligibilityResolver {

    private final SectionAbsenceLimitPort sectionAbsenceLimitPort;

    public FinalExamEligibilityResolver(SectionAbsenceLimitPort sectionAbsenceLimitPort) {
        this.sectionAbsenceLimitPort = sectionAbsenceLimitPort;
    }

    public FinalExamEligibilityResponse resolve(SubjectSectionEnrollment enrollment) {
        return resolve(enrollment.hasRegularCoursework(),
                () -> sectionAbsenceLimitPort.exceedsAbsenceLimit(enrollment.getId()));
    }

    public FinalExamEligibilityResponse resolve(CourseSubjectSectionEnrollment enrollment) {
        return resolve(enrollment.hasRegularCoursework(),
                () -> sectionAbsenceLimitPort.exceedsCourseAbsenceLimit(enrollment.getId()));
    }

    private FinalExamEligibilityResponse resolve(boolean regularCoursework, BooleanSupplier exceedsAbsenceLimit) {
        if (!regularCoursework) {
            return FinalExamEligibilityResponse.blockedBy(FinalExamIneligibilityReason.COURSEWORK_NOT_REGULAR);
        }
        if (exceedsAbsenceLimit.getAsBoolean()) {
            return FinalExamEligibilityResponse.blockedBy(FinalExamIneligibilityReason.FREE_BY_ABSENCES);
        }
        return FinalExamEligibilityResponse.allowed();
    }
}
