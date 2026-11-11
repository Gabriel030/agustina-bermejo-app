package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.shared.domain.Grade;

public record CourseworkOutcome(EnrollmentStatus status, Grade finalScore, FinalGradeType finalGradeType) {

    public boolean passesCoursework() {
        return status == EnrollmentStatus.REGULAR
                || (status == EnrollmentStatus.APPROVED && finalGradeType == FinalGradeType.PROMOTION);
    }
}
