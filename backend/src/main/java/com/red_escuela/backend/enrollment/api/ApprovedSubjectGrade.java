package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDate;

public record ApprovedSubjectGrade(
        Long subjectId, String subjectName, Integer curricularYear, ApprovalSource approvalSource,
        Grade grade, LocalDate approvedOn) {

    public boolean hasGrade() {
        return grade != null;
    }

    public boolean hasApprovalDate() {
        return approvedOn != null;
    }
}
