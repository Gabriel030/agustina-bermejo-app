package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDate;

public record RecognizedEquivalence(
        Long id, Long subjectId, String originInstitution, String originSubjectName, Grade originScore,
        LocalDate recognizedOn, String resolutionNumber) {
}
