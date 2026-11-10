package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse.DocumentationSummary;

import java.util.List;

public record ProgramEnrollmentApplicationApprovalCheckResponse(Long applicationId, boolean canApprove, DocumentationSummary documentation, DebtCheck debt) {

    public record DebtCheck(boolean blocksApproval, List<String> violationCodes) {
    }
}
