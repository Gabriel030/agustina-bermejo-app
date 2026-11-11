package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;

import java.util.Collection;
import java.util.List;

public final class PrerequisiteRequirementPolicy {

    public List<EnrollmentStatus> relevantStatuses() {
        return List.of(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED);
    }

    public PrerequisiteStanding standingOf(Collection<EnrollmentStatus> studentStatuses) {
        if (studentStatuses.contains(EnrollmentStatus.APPROVED)) {
            return PrerequisiteStanding.APPROVED;
        }
        if (studentStatuses.contains(EnrollmentStatus.REGULAR)) {
            return PrerequisiteStanding.REGULAR;
        }
        return PrerequisiteStanding.NOT_TAKEN;
    }

    public boolean isMet(RequirementType requirementType, PrerequisiteStanding standing) {
        return switch (standing) {
            case WAIVED, APPROVED -> true;
            case REGULAR -> requirementType != RequirementType.APPROVED;
            case NOT_TAKEN -> false;
        };
    }
}
