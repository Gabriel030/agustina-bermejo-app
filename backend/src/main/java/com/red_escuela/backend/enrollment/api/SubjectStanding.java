package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.Grade;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record SubjectStanding(List<CourseworkEntry> coursework, RecognizedEquivalence equivalence) {

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

    public Grade bestApprovedScore() {
        return Stream.concat(
                        coursework.stream().filter(entry -> entry.status() == EnrollmentStatus.APPROVED)
                                .map(CourseworkEntry::finalScore),
                        Stream.ofNullable(equivalence).map(RecognizedEquivalence::originScore))
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    public Grade approvedScore() {
        return coursework.stream().filter(entry -> entry.status() == EnrollmentStatus.APPROVED)
                .map(CourseworkEntry::finalScore).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> isApprovedByCoursework() || equivalence == null ? null : equivalence.originScore());
    }
}
