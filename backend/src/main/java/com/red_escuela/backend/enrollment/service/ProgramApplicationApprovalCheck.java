package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse.DebtCheck;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse.DocumentationSummary;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentsIncompleteException;
import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;
import com.red_escuela.backend.identity.exception.StudentNotActiveException;
import com.red_escuela.backend.identity.exception.StudentNotConfirmedException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
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
        List<String> blockingReasons = blockingReasonsOf(application);
        boolean canApprove = blockingReasons.isEmpty() && documentation.complete() && !debt.blocksApproval();
        return new ProgramEnrollmentApplicationApprovalCheckResponse(applicationId, canApprove, documentation, debt, blockingReasons);
    }

    private List<String> blockingReasonsOf(ProgramEnrollmentApplication application) {
        try {
            application.requirePending();
            programEnrollmentService.validateStudentCanEnrollInProgram(application.getStudent(), application.getProgram());
            return List.of();
        } catch (InvalidProgramEnrollmentApplicationStateException | StudentNotConfirmedException | StudentNotActiveException
                 | ApplicationDocumentsIncompleteException | AlreadyEnrolledInProgramException blocked) {
            return List.of(ErrorCodeResponse.codeOf(blocked.getClass()));
        }
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
