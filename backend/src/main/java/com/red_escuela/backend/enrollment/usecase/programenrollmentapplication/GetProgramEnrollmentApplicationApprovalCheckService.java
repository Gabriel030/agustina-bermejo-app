package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.billing.api.EnrollmentDebtBlockPreview;
import com.red_escuela.backend.billing.api.EnrollmentDebtBlockView;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse.DebtCheck;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse.DocumentationSummary;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.service.ProgramApplicationReviewViews;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramEnrollmentApplicationApprovalCheckService implements GetProgramEnrollmentApplicationApprovalCheckUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramApplicationReviewViews reviewViews;
    private final EnrollmentDebtBlockPreview debtBlockPreview;

    public GetProgramEnrollmentApplicationApprovalCheckService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                               ProgramApplicationReviewViews reviewViews,
                                                               EnrollmentDebtBlockPreview debtBlockPreview) {
        this.applicationFinder = applicationFinder;
        this.reviewViews = reviewViews;
        this.debtBlockPreview = debtBlockPreview;
    }

    @Override
    public ProgramEnrollmentApplicationApprovalCheckResponse get(Long applicationId) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(applicationId);
        Long studentId = application.getStudent().getId();
        DocumentationSummary documentation = reviewViews.documentationOf(studentId);
        EnrollmentDebtBlockView debt = debtBlockPreview.previewFor(studentId);
        return new ProgramEnrollmentApplicationApprovalCheckResponse(applicationId, documentation.complete() && !debt.blocked(),
                documentation, new DebtCheck(debt.blocked(), debt.violationCodes()));
    }
}
