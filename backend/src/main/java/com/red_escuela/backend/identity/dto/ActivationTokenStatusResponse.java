package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.identity.domain.ActivationTokenStatus;

public record ActivationTokenStatusResponse(ActivationTokenStatus status, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String username,
                                            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) ActivationInstitutionContact institution) {

    public static ActivationTokenStatusResponse invalid() {
        return new ActivationTokenStatusResponse(ActivationTokenStatus.INVALID, null, null);
    }

    public static ActivationTokenStatusResponse expired(ActivationInstitutionContact institution) {
        return new ActivationTokenStatusResponse(ActivationTokenStatus.EXPIRED, null, institution);
    }

    public static ActivationTokenStatusResponse valid(String username, ActivationInstitutionContact institution) {
        return new ActivationTokenStatusResponse(ActivationTokenStatus.VALID, username, institution);
    }
}
