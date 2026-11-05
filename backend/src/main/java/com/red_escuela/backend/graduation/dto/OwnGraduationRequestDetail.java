package com.red_escuela.backend.graduation.dto;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record OwnGraduationRequestDetail(
        Long id, String status, LocalDateTime requestedAt, @Schema(nullable = true) LocalDateTime verifiedAt,
        @Schema(nullable = true) LocalDateTime decidedAt, @Schema(nullable = true) String rejectionReason) {

    public static OwnGraduationRequestDetail from(GraduationRequest request) {
        return new OwnGraduationRequestDetail(request.getId(), request.getStatus().name(), request.getRequestedAt(),
                request.getVerifiedAt(), request.getDecidedAt(), request.getRejectionReason());
    }
}
