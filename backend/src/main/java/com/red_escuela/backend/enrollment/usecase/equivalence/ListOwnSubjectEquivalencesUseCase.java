package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.OwnSubjectEquivalenceResponse;

import java.util.List;

public interface ListOwnSubjectEquivalencesUseCase {
    List<OwnSubjectEquivalenceResponse> list(Long studentId, Long programEnrollmentId);
}
