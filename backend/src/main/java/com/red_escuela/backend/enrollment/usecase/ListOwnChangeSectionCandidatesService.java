package com.red_escuela.backend.enrollment.usecase;

import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse;
import com.red_escuela.backend.enrollment.service.OwnChangeSectionCandidatesFinder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListOwnChangeSectionCandidatesService implements ListOwnChangeSectionCandidates {

    private final OwnChangeSectionCandidatesFinder finder;

    public ListOwnChangeSectionCandidatesService(OwnChangeSectionCandidatesFinder finder) {
        this.finder = finder;
    }

    @Override
    public List<ChangeSectionCandidateResponse> list(Long studentId, Long sectionEnrollmentId) {
        return finder.find(studentId, sectionEnrollmentId);
    }
}
