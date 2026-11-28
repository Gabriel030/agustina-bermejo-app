package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record SubjectStanding(List<CourseworkEntry> coursework, RecognizedEquivalence equivalence) {

    private static final Comparator<CourseworkEntry> LATEST_APPROVAL = Comparator
            .comparing(CourseworkEntry::finalScoreDate, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(CourseworkEntry::enrollmentId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

    public static final SubjectStanding NONE = new SubjectStanding(List.of(), null);

    public List<EnrollmentStatus> courseworkStatuses() {
        return coursework.stream().map(CourseworkEntry::status).toList();
    }

    public boolean isApprovedByCoursework() {
        return courseworkStatuses().contains(EnrollmentStatus.APPROVED);
    }

    public boolean isApprovedByEquivalence() {
        return equivalence != null;
    }

    public boolean isApproved() {
        return isApprovedByCoursework() || isApprovedByEquivalence();
    }

    public boolean isApprovedOrRegular() {
        return isApproved() || courseworkStatuses().contains(EnrollmentStatus.REGULAR);
    }

    public ApprovalSource approvalSource() {
        if (isApprovedByCoursework()) {
            return ApprovalSource.COURSEWORK;
        }
        return isApprovedByEquivalence() ? ApprovalSource.EQUIVALENCE : null;
    }

    public Grade scoreForPrerequisiteCheck() {
        if (equivalence != null && equivalence.originScore() == null) {
            return null;
        }
        return approvedScore();
    }

    public Grade approvedScore() {
        Optional<CourseworkEntry> latest = coursework.stream()
                .filter(entry -> entry.status() == EnrollmentStatus.APPROVED)
                .max(LATEST_APPROVAL);
        if (latest.isPresent()) {
            return latest.get().finalScore();
        }
        return equivalence == null ? null : equivalence.originScore();
    }
}
