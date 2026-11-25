package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;

import com.red_escuela.backend.shared.domain.Grade;

import java.util.Collection;
import java.util.List;

public final class PrerequisiteRequirementPolicy {

    public List<EnrollmentStatus> relevantStatuses() {
        return List.of(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED);
    }

    public PrerequisiteStanding standingOf(Collection<EnrollmentStatus> studentStatuses) {
        return standingOf(studentStatuses, false);
    }

    public PrerequisiteStanding standingOf(Collection<EnrollmentStatus> studentStatuses, boolean approvedByEquivalence) {
        if (approvedByEquivalence || studentStatuses.contains(EnrollmentStatus.APPROVED)) {
            return PrerequisiteStanding.APPROVED;
        }
        if (studentStatuses.contains(EnrollmentStatus.REGULAR)) {
            return PrerequisiteStanding.REGULAR;
        }
        return PrerequisiteStanding.NOT_TAKEN;
    }

    public boolean isMetForApproval(PrerequisiteStanding standing) {
        return standing == PrerequisiteStanding.APPROVED || standing == PrerequisiteStanding.WAIVED;
    }

    public boolean isMet(RequirementType requirementType, PrerequisiteStanding standing) {
        return isMet(requirementType, standing, null, null);
    }

    public boolean isMet(RequirementType requirementType, PrerequisiteStanding standing, Grade minGrade,
            Grade bestApprovedScore) {
        if (!isMetIgnoringGrade(requirementType, standing)) {
            return false;
        }
        boolean gradeApplies = requirementType == RequirementType.APPROVED && standing == PrerequisiteStanding.APPROVED;
        if (!gradeApplies || minGrade == null || bestApprovedScore == null) {
            return true;
        }
        return bestApprovedScore.meets(minGrade);
    }

    private boolean isMetIgnoringGrade(RequirementType requirementType, PrerequisiteStanding standing) {
        return switch (standing) {
            case WAIVED, APPROVED -> true;
            case REGULAR -> requirementType != RequirementType.APPROVED;
            case NOT_TAKEN -> false;
        };
    }
}
