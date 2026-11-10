package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationPage;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.InvalidResolvedApplicationStatusException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.service.ProgramApplicationReviewViews;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Locale;

@UseCase
public class ListResolvedProgramEnrollmentApplicationsService implements ListResolvedProgramEnrollmentApplicationsUseCase {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<ProgramEnrollmentApplicationStatus> RESOLVED = List.of(
            ProgramEnrollmentApplicationStatus.APPROVED, ProgramEnrollmentApplicationStatus.REJECTED, ProgramEnrollmentApplicationStatus.EXPIRED);

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramApplicationReviewViews reviewViews;

    public ListResolvedProgramEnrollmentApplicationsService(ProgramEnrollmentApplicationRepository applicationRepository,
                                                            ProgramApplicationReviewViews reviewViews) {
        this.applicationRepository = applicationRepository;
        this.reviewViews = reviewViews;
    }

    @Override
    public ProgramEnrollmentApplicationPage list(ProgramEnrollmentApplicationStatus status, String search, int page, int size) {
        if (status == ProgramEnrollmentApplicationStatus.PENDING) {
            throw new InvalidResolvedApplicationStatusException();
        }
        List<ProgramEnrollmentApplicationStatus> statuses = status == null ? RESOLVED : List.of(status);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<ProgramEnrollmentApplication> result = applicationRepository.searchByStatuses(statuses, patternOf(search), pageRequest);
        return new ProgramEnrollmentApplicationPage(reviewViews.viewsOf(result.getContent()), result.getTotalElements());
    }

    private String patternOf(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
