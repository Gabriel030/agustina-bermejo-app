package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceStatusFilter;

import java.util.List;

public interface ListSubjectEquivalencesUseCase {
    List<SubjectEquivalenceResponse> list(Long programEnrollmentId, SubjectEquivalenceStatusFilter filter);
}
