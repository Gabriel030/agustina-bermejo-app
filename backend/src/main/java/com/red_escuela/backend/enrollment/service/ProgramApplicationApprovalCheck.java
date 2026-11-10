package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse.DebtCheck;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse.DocumentationSummary;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProgramApplicationApprovalCheck {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramApplicationReviewViews reviewViews;
    private final ProgramEnrollmentService programEnrollmentService;

    public ProgramApplicationApprovalCheck(ProgramEnrollmentApplicationFinder applicationFinder,
                                           ProgramApplicationReviewViews reviewViews,
                                           ProgramEnrollmentService programEnrollmentService) {
        this.applicationFinder = applicationFinder;
        this.reviewViews = reviewViews;
        this.programEnrollmentService = programEnrollmentService;
    }

    public ProgramEnrollmentApplicationApprovalCheckResponse check(Long applicationId) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(applicationId);
        Long studentId = application.getStudent().getId();
        DocumentationSummary documentation = reviewViews.documentationOf(studentId);
        DebtCheck debt = debtCheckOf(studentId);
        return new ProgramEnrollmentApplicationApprovalCheckResponse(applicationId, documentation.complete() && !debt.blocksApproval(), documentation, debt);
    }

    private DebtCheck debtCheckOf(Long studentId) {
        try {
            programEnrollmentService.verifyStudentHasNoOutstandingDebt(studentId);
            return new DebtCheck(false, List.of());
        } catch (ConflictException blocked) {
            if (blocked instanceof ReportsPolicyViolations reporting) {
                return new DebtCheck(true, reporting.violations().stream().map(PolicyViolation::code).toList());
            }
            throw blocked;
        }
    }
}
