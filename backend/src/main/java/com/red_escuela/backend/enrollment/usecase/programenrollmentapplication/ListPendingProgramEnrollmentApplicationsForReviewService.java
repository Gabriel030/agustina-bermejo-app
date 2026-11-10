package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.service.ProgramApplicationReviewViews;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPendingProgramEnrollmentApplicationsForReviewService implements ListPendingProgramEnrollmentApplicationsForReviewUseCase {

    private final ProgramApplicationReviewViews reviewViews;

    public ListPendingProgramEnrollmentApplicationsForReviewService(ProgramApplicationReviewViews reviewViews) {
        this.reviewViews = reviewViews;
    }

    @Override
    public List<ProgramEnrollmentApplicationResponse> list() {
        return reviewViews.pendingViews();
    }
}
