package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;

public interface VoidSubjectEquivalenceUseCase {
    SubjectEquivalenceResponse voidEquivalence(VoidSubjectEquivalenceCommand command);
}
