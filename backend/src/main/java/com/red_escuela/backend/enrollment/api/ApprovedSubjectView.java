package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;

public record ApprovedSubjectView(ProgramSubjectView subject, ApprovalSource approvalSource) {
}
