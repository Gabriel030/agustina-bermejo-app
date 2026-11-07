package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDateTime;

public record GradeItemCorrectionView(Grade previousScore, String reason, LocalDateTime correctedAt) {
}
