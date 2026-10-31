package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDateTime;

public record GradeItemView(
        Long id, Long enrollmentId, String type, Integer sequenceNumber,
        Grade score, LocalDateTime date, Long examBoardId) {
}
