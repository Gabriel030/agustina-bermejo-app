package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;

import java.util.Collection;

public final class CurriculumSubjectStatePolicy {

    public CurriculumSubjectState stateOf(Collection<EnrollmentStatus> statuses, boolean prerequisitesMet) {
        return stateOf(statuses, false, prerequisitesMet);
    }

    public CurriculumSubjectState stateOf(Collection<EnrollmentStatus> statuses, boolean approvedByEquivalence,
            boolean prerequisitesMet) {
        if (approvedByEquivalence || statuses.contains(EnrollmentStatus.APPROVED)) {
            return CurriculumSubjectState.APPROVED;
        }
        if (statuses.contains(EnrollmentStatus.ENROLLED) || statuses.contains(EnrollmentStatus.IN_PROGRESS)) {
            return CurriculumSubjectState.IN_PROGRESS;
        }
        if (statuses.contains(EnrollmentStatus.REGULAR)) {
            return CurriculumSubjectState.REGULAR_PENDING_FINAL;
        }
        if (statuses.contains(EnrollmentStatus.FAILED)) {
            return CurriculumSubjectState.FAILED;
        }
        return prerequisitesMet ? CurriculumSubjectState.AVAILABLE : CurriculumSubjectState.BLOCKED;
    }

    public boolean countsAsAttempt(EnrollmentStatus status) {
        return switch (status) {
            case ENROLLED, IN_PROGRESS, REGULAR, APPROVED, FAILED -> true;
            default -> false;
        };
    }
}
