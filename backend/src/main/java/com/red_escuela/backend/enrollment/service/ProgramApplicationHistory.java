package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationPage;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.InvalidResolvedApplicationStatusException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ProgramApplicationHistory {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<ProgramEnrollmentApplicationStatus> RESOLVED = List.of(
            ProgramEnrollmentApplicationStatus.APPROVED, ProgramEnrollmentApplicationStatus.REJECTED, ProgramEnrollmentApplicationStatus.EXPIRED);

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramApplicationReviewViews reviewViews;

    public ProgramApplicationHistory(ProgramEnrollmentApplicationRepository applicationRepository, ProgramApplicationReviewViews reviewViews) {
        this.applicationRepository = applicationRepository;
        this.reviewViews = reviewViews;
    }

    @Transactional(readOnly = true)
    public ProgramEnrollmentApplicationPage searchResolved(String status, String search, int page, int size) {
        List<ProgramEnrollmentApplicationStatus> statuses = statusesOf(status);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<ProgramEnrollmentApplication> result = applicationRepository.searchByStatuses(statuses, patternOf(search), pageRequest);
        return new ProgramEnrollmentApplicationPage(reviewViews.viewsOf(result.getContent()), result.getTotalElements());
    }

    private List<ProgramEnrollmentApplicationStatus> statusesOf(String status) {
        if (status == null || status.isBlank()) {
            return RESOLVED;
        }
        try {
            ProgramEnrollmentApplicationStatus parsed = ProgramEnrollmentApplicationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            if (!RESOLVED.contains(parsed)) {
                throw new InvalidResolvedApplicationStatusException();
            }
            return List.of(parsed);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidResolvedApplicationStatusException();
        }
    }

    private String patternOf(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
