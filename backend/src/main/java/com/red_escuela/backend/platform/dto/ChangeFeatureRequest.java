package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.service.FeatureChangeReasonValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeFeatureRequest(
        Boolean enabled,
        BillingMode mode,
        @NotBlank @Size(min = FeatureChangeReasonValidator.MIN_LENGTH, max = FeatureChangeReasonValidator.MAX_LENGTH) String reason,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 200) String previewConfirmationToken) {
}
