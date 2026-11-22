package com.red_escuela.backend.enrollment.usecase;

import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse;

import java.util.List;

public interface ListOwnChangeSectionCandidates {

    List<ChangeSectionCandidateResponse> list(Long studentId, Long sectionEnrollmentId);
}
