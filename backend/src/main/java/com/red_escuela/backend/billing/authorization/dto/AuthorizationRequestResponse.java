package com.red_escuela.backend.billing.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;

public record AuthorizationRequestResponse(Long id, String operationType, String status, @Schema(nullable = true) String resumeOutcome) {

    public static AuthorizationRequestResponse from(AuthorizationRequest request) {
        return new AuthorizationRequestResponse(request.getId(), request.getOperationType().name(), request.getStatus().name(),
                request.getResumeOutcome() == null ? null : request.getResumeOutcome().name());
    }
}
