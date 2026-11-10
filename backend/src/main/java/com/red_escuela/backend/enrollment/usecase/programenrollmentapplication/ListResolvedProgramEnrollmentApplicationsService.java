package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationPage;
import com.red_escuela.backend.enrollment.service.ProgramApplicationHistory;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListResolvedProgramEnrollmentApplicationsService implements ListResolvedProgramEnrollmentApplicationsUseCase {

    private final ProgramApplicationHistory history;

    public ListResolvedProgramEnrollmentApplicationsService(ProgramApplicationHistory history) {
        this.history = history;
    }

    @Override
    public ProgramEnrollmentApplicationPage list(String status, String search, int page, int size) {
        return history.searchResolved(status, search, page, size);
    }
}
