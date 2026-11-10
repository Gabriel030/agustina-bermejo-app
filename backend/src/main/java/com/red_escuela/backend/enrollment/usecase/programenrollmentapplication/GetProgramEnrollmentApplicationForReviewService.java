package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.service.ProgramApplicationReviewViews;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramEnrollmentApplicationForReviewService implements GetProgramEnrollmentApplicationForReviewUseCase {

    private final ProgramApplicationReviewViews reviewViews;

    public GetProgramEnrollmentApplicationForReviewService(ProgramApplicationReviewViews reviewViews) {
        this.reviewViews = reviewViews;
    }

    @Override
    public ProgramEnrollmentApplicationResponse get(Long id) {
        return reviewViews.viewOfApplication(id);
    }
}
