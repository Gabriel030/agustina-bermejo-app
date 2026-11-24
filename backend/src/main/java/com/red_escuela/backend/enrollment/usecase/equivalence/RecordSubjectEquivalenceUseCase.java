package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;

public interface RecordSubjectEquivalenceUseCase {
    SubjectEquivalenceResponse record(RecordSubjectEquivalenceCommand command);
}
